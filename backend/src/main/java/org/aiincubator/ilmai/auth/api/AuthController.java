package org.aiincubator.ilmai.auth.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.auth.config.AuthProperties;
import org.aiincubator.ilmai.auth.payload.DevLoginRequest;
import org.aiincubator.ilmai.auth.payload.GoogleLoginRequest;
import org.aiincubator.ilmai.auth.payload.LogoutRequest;
import org.aiincubator.ilmai.auth.payload.MeResponse;
import org.aiincubator.ilmai.auth.payload.RefreshRequest;
import org.aiincubator.ilmai.auth.payload.TokenPairResponse;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.auth.service.AuthException;
import org.aiincubator.ilmai.auth.service.AuthService;
import org.aiincubator.ilmai.common.payload.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthProperties authProperties;

    @PostMapping("/google")
    public ApiResponse<TokenPairResponse> loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request) {
        return ApiResponse.ok(authService.loginWithGoogle(request.getIdToken()));
    }

    @PostMapping("/dev")
    public ApiResponse<TokenPairResponse> loginAsDev(@Valid @RequestBody(required = false) DevLoginRequest request) {
        AuthProperties.DevLogin dev = authProperties.getDevLogin();
        if (!dev.isEnabled()) {
            throw new AuthException(AuthException.Reason.DEV_LOGIN_DISABLED);
        }
        String email = request != null && StringUtils.hasText(request.getEmail())
                ? request.getEmail()
                : dev.getEmail();
        String name = request != null && StringUtils.hasText(request.getName())
                ? request.getName()
                : dev.getName();
        return ApiResponse.ok(authService.loginAsDev(email, name));
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenPairResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.ok(authService.refresh(request.getRefreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ApiResponse<MeResponse> me(@AuthenticationPrincipal CurrentUser currentUser) {
        return ApiResponse.ok(authService.getMe(currentUser));
    }
}
