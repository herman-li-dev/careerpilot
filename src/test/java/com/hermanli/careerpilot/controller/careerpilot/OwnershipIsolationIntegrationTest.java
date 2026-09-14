package com.hermanli.careerpilot.controller.careerpilot;

import com.hermanli.careerpilot.api.ApiResponse;
import com.hermanli.careerpilot.api.ResourceNotFoundException;
import com.hermanli.careerpilot.identity.CurrentUserId;
import com.hermanli.careerpilot.identity.RegisteredUser;
import com.hermanli.careerpilot.identity.SessionCookieService;
import com.hermanli.careerpilot.identity.SessionTokenService;
import com.hermanli.careerpilot.identity.UserAccountService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.flyway.enabled=true")
@AutoConfigureMockMvc
@Import(OwnershipIsolationIntegrationTest.OwnershipFixtureController.class)
@Tag("external")
@Transactional
class OwnershipIsolationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountService userAccountService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SessionTokenService sessionTokenService;

    @Test
    void unauthenticatedAndCrossUserReadsDoNotRevealOwnedProfiles() throws Exception {
        Account userA = createAccountWithProfile(8);
        Account userB = createAccountWithProfile(12);
        Cookie userACookie = sessionCookie(userA.id());

        mockMvc.perform(get("/users/ownership-fixture/{profileUserId}", userA.id()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"));

        mockMvc.perform(get("/users/ownership-fixture/{profileUserId}", userA.id())
                        .cookie(userACookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(userA.id()))
                .andExpect(jsonPath("$.data.weeklyHours").value(8));

        mockMvc.perform(get("/users/ownership-fixture/{profileUserId}", userB.id())
                        .cookie(userACookie))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void crossUserUpdatesReturnNotFoundAndLeaveTheOwnersRecordUnchanged() throws Exception {
        Account userA = createAccountWithProfile(8);
        Account userB = createAccountWithProfile(12);
        Cookie userACookie = sessionCookie(userA.id());
        Cookie userBCookie = sessionCookie(userB.id());

        mockMvc.perform(put("/users/ownership-fixture/{profileUserId}", userB.id())
                        .cookie(userACookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weeklyHours\":20}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
        assertEquals(12, weeklyHours(userB.id()));

        mockMvc.perform(put("/users/ownership-fixture/{profileUserId}", userB.id())
                        .cookie(userBCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weeklyHours\":20}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(userB.id()))
                .andExpect(jsonPath("$.data.weeklyHours").value(20));
        assertEquals(20, weeklyHours(userB.id()));
    }

    private Account createAccountWithProfile(int weeklyHours) {
        String email = "i04-" + UUID.randomUUID() + "@example.com";
        String password = UUID.randomUUID().toString();
        RegisteredUser user = userAccountService.register(email, password);
        jdbcTemplate.update(
                "insert into user_profile (user_id, weekly_hours) values (?, ?)",
                user.id(),
                weeklyHours
        );
        return new Account(user.id());
    }

    private Cookie sessionCookie(long userId) {
        Cookie cookie = new Cookie(SessionCookieService.COOKIE_NAME, sessionTokenService.issue(userId));
        cookie.setHttpOnly(true);
        cookie.setPath("/api");
        return cookie;
    }

    private int weeklyHours(long userId) {
        return jdbcTemplate.queryForObject(
                "select weekly_hours from user_profile where user_id = ?",
                Integer.class,
                userId
        );
    }

    private record Account(long id) {
    }

    @RestController
    @RequestMapping("/users/ownership-fixture")
    static class OwnershipFixtureController {

        private final JdbcTemplate jdbcTemplate;

        OwnershipFixtureController(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }

        @GetMapping("/{profileUserId}")
        ApiResponse<OwnedProfile> getProfile(
                @PathVariable long profileUserId,
                @CurrentUserId long currentUserId
        ) {
            return ApiResponse.success(findOwnedProfile(profileUserId, currentUserId));
        }

        @PutMapping("/{profileUserId}")
        ApiResponse<OwnedProfile> updateProfile(
                @PathVariable long profileUserId,
                @CurrentUserId long currentUserId,
                @RequestBody UpdateProfile request
        ) {
            int updated = jdbcTemplate.update(
                    """
                    update user_profile
                    set weekly_hours = ?, updated_at = now()
                    where user_id = ? and user_id = ?
                    """,
                    request.weeklyHours(),
                    profileUserId,
                    currentUserId
            );
            if (updated == 0) {
                throw new ResourceNotFoundException();
            }
            return ApiResponse.success(findOwnedProfile(profileUserId, currentUserId));
        }

        private OwnedProfile findOwnedProfile(long profileUserId, long currentUserId) {
            List<OwnedProfile> profiles = jdbcTemplate.query(
                    """
                    select user_id, weekly_hours
                    from user_profile
                    where user_id = ? and user_id = ?
                    """,
                    (resultSet, rowNumber) -> new OwnedProfile(
                            resultSet.getLong("user_id"),
                            resultSet.getInt("weekly_hours")
                    ),
                    profileUserId,
                    currentUserId
            );
            if (profiles.isEmpty()) {
                throw new ResourceNotFoundException();
            }
            return profiles.getFirst();
        }
    }

    record OwnedProfile(long userId, int weeklyHours) {
    }

    record UpdateProfile(int weeklyHours) {
    }
}
