package com.example.auth_bff.controller;

import com.example.auth_bff.dto.AccessTokenResponse;
import com.example.auth_bff.dto.UserResponse;
import com.example.auth_bff.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/bff/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @GetMapping("/login")
    public ResponseEntity<AccessTokenResponse> login(@AuthenticationPrincipal OAuth2User principal) {
        AccessTokenResponse response = authService.getAccessToken(principal);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            HttpServletRequest request,
            HttpServletResponse response,
            @AuthenticationPrincipal OAuth2User principal) {

        String username = (principal != null) ? principal.getName() : "anonymous";
        boolean wasAuthenticated = (principal != null);

        // セッション無効化
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
            System.out.println("Session invalidated for user: " + username);
        } else {
            System.out.println("No active session found for user: " + username);
        }

        // セキュリティコンテキストクリア
        SecurityContextHolder.clearContext();

        // クッキー削除
        Cookie sessionCookie = new Cookie("BFFSESSIONID", null);
        sessionCookie.setPath("/");
        sessionCookie.setHttpOnly(true);
        sessionCookie.setMaxAge(0);
        response.addCookie(sessionCookie);

        Map<String, String> result = new HashMap<>();
        result.put("message", "Logged out successfully");
        result.put("user", username);
        result.put("wasAuthenticated", String.valueOf(wasAuthenticated));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/user")
    public ResponseEntity<UserResponse> user(@AuthenticationPrincipal OAuth2User principal) {
        UserResponse response = authService.getUserInfo(principal);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AccessTokenResponse> refresh(@AuthenticationPrincipal OAuth2User principal) {
        AccessTokenResponse response = authService.refreshAccessToken(principal);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> status = new HashMap<>();
        status.put("status", "UP");
        status.put("service", "auth-bff");
        return ResponseEntity.ok(status);
    }
}