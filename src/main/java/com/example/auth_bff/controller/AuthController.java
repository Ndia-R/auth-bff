package com.example.auth_bff.controller;

import com.example.auth_bff.dto.LogoutResponse;
import com.example.auth_bff.dto.UserResponse;
import com.example.auth_bff.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@RestController
@RequestMapping("/bff/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    /**
     * ログイン開始エンドポイント
     *
     * Spring Securityの認証状態に基づいてリダイレクト先を決定します。
     * - 認証済み（principal != null）: フロントエンドにリダイレクト
     * - 未認証（principal == null）: OAuth2認証フローを開始
     */
    @GetMapping("/login")
    public void startLogin(
        HttpServletResponse response,
        @AuthenticationPrincipal OAuth2User principal
    )
        throws IOException {
        if (principal != null) {
            // 既にログイン済みの場合、直接フロントエンドにリダイレクト
            response.sendRedirect(frontendUrl + "/auth-callback");
        } else {
            // 未認証の場合、OAuth2認証フローを開始
            response.sendRedirect("/oauth2/authorization/keycloak");
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<LogoutResponse> logout(
        HttpServletRequest request,
        HttpServletResponse response,
        @AuthenticationPrincipal OAuth2User principal,
        @RequestParam(value = "complete", defaultValue = "false") boolean complete
    ) {
        LogoutResponse logoutResponse = authService.logout(request, response, principal, complete);
        return ResponseEntity.ok(logoutResponse);
    }

    @GetMapping("/user")
    public ResponseEntity<UserResponse> user(@AuthenticationPrincipal OAuth2User principal) {
        UserResponse response = authService.getUserInfo(principal);
        return ResponseEntity.ok(response);
    }
}