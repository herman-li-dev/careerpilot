package com.hermanli.careerpilot.controller.careerpilot;

import com.hermanli.careerpilot.api.ApiResponse;
import com.hermanli.careerpilot.identity.CurrentUser;
import com.hermanli.careerpilot.identity.InvalidCredentialsException;
import com.hermanli.careerpilot.identity.RegisteredUser;
import com.hermanli.careerpilot.identity.SessionCookieService;
import com.hermanli.careerpilot.identity.SessionTokenService;
import com.hermanli.careerpilot.identity.UserAccountService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserAccountService userAccountService;
    private final SessionTokenService sessionTokenService;
    private final SessionCookieService sessionCookieService;

    public AuthController(
            UserAccountService userAccountService,
            SessionTokenService sessionTokenService,
            SessionCookieService sessionCookieService
    ) {
        this.userAccountService = userAccountService;
        this.sessionTokenService = sessionTokenService;
        this.sessionCookieService = sessionCookieService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisteredUser>> register(
            @Valid @RequestBody RegistrationRequest request
    ) {
        RegisteredUser registeredUser = userAccountService.register(request.email(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(registeredUser));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<CurrentUser>> login(
            @Valid @RequestBody LoginRequest request
    ) {
        RegisteredUser user = userAccountService.authenticate(request.email(), request.password())
                .orElseThrow(InvalidCredentialsException::new);
        CurrentUser currentUser = userAccountService.findCurrentUser(user.id())
                .orElseThrow(InvalidCredentialsException::new);
        String token = sessionTokenService.issue(user.id());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, sessionCookieService.create(token).toString())
                .body(ApiResponse.success(currentUser));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, sessionCookieService.clear().toString())
                .build();
    }

    public record RegistrationRequest(
            @NotBlank(message = "Email is required.")
            @Email(message = "Email must be valid.")
            @Size(max = 320, message = "Email must not exceed 320 characters.")
            String email,

            @NotBlank(message = "Password is required.")
            @Size(min = 8, max = 72, message = "Password must contain between 8 and 72 characters.")
            String password
    ) {
        public RegistrationRequest {
            if (email != null) {
                email = email.strip();
            }
        }
    }

    public record LoginRequest(
            @NotBlank(message = "Email is required.")
            @Email(message = "Email must be valid.")
            @Size(max = 320, message = "Email must not exceed 320 characters.")
            String email,

            @NotBlank(message = "Password is required.")
            @Size(max = 72, message = "Password must not exceed 72 characters.")
            String password
    ) {
        public LoginRequest {
            if (email != null) {
                email = email.strip();
            }
        }
    }
}
