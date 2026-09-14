package com.hermanli.careerpilot.controller.careerpilot;

import com.hermanli.careerpilot.api.ApiResponse;
import com.hermanli.careerpilot.demo.careerpilot.DemoModeService;
import com.hermanli.careerpilot.identity.CurrentUser;
import com.hermanli.careerpilot.identity.SessionCookieService;
import com.hermanli.careerpilot.identity.SessionTokenService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@ConditionalOnProperty(prefix = "careerpilot.demo", name = "enabled", havingValue = "true")
public class DemoAuthController {

    private final DemoModeService demoModeService;
    private final SessionTokenService sessionTokenService;
    private final SessionCookieService sessionCookieService;

    public DemoAuthController(
            DemoModeService demoModeService,
            SessionTokenService sessionTokenService,
            SessionCookieService sessionCookieService
    ) {
        this.demoModeService = demoModeService;
        this.sessionTokenService = sessionTokenService;
        this.sessionCookieService = sessionCookieService;
    }

    @PostMapping("/demo-login")
    public ResponseEntity<ApiResponse<CurrentUser>> login() {
        CurrentUser currentUser = demoModeService.currentUser();
        String token = sessionTokenService.issue(currentUser.id());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, sessionCookieService.create(token).toString())
                .body(ApiResponse.success(currentUser));
    }
}
