package com.hermanli.careerpilot.controller.careerpilot;

import com.hermanli.careerpilot.api.ApiResponse;
import com.hermanli.careerpilot.identity.AuthenticationRequiredException;
import com.hermanli.careerpilot.identity.CurrentUserId;
import com.hermanli.careerpilot.identity.CurrentUser;
import com.hermanli.careerpilot.identity.UserAccountService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserAccountService userAccountService;

    public UserController(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUser> currentUser(
            @CurrentUserId long userId
    ) {
        CurrentUser currentUser = userAccountService.findCurrentUser(userId)
                .orElseThrow(AuthenticationRequiredException::new);
        return ApiResponse.success(currentUser);
    }
}
