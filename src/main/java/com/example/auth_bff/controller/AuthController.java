package com.example.auth_bff.controller;

import com.example.auth_bff.dto.AccessTokenResponse;
import com.example.auth_bff.dto.UserResponse;
import com.example.auth_bff.exception.UnauthorizedException;
import com.example.auth_bff.service.TokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final TokenService tokenService;

    @GetMapping("/login")
    public ResponseEntity<AccessTokenResponse> login(@AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) {
            throw new UnauthorizedException("認証が必要です");
        }

        String principalName = principal.getAttribute("preferred_username");
        if (principalName == null) {
            principalName = principal.getName();
        }

        String accessToken = tokenService.getAccessToken(principalName);
        long expiresIn = tokenService.getExpiresIn(principalName);
        String tokenType = tokenService.getTokenType(principalName);

        AccessTokenResponse response = new AccessTokenResponse(
            accessToken,
            (int) expiresIn,
            tokenType
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }

        SecurityContextHolder.clearContext();

        Map<String, String> result = new HashMap<>();
        result.put("message", "Logged out successfully");
        return ResponseEntity.ok(result);
    }

    @GetMapping("/user")
    public ResponseEntity<UserResponse> user(@AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) {
            throw new UnauthorizedException("認証が必要です");
        }

        UserResponse userResponse = new UserResponse(
            principal.getAttribute("name"),
            principal.getAttribute("email"),
            principal.getAttribute("preferred_username")
        );

        return ResponseEntity.ok(userResponse);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AccessTokenResponse> refresh(@AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) {
            throw new UnauthorizedException("認証が必要です");
        }

        String principalName = principal.getAttribute("preferred_username");
        if (principalName == null) {
            principalName = principal.getName();
        }

        AccessTokenResponse response = tokenService.refreshAccessToken(principalName);

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