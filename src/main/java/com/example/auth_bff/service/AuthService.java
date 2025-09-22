package com.example.auth_bff.service;

import com.example.auth_bff.dto.AccessTokenResponse;
import com.example.auth_bff.dto.UserResponse;
import com.example.auth_bff.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpSession;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final TokenService tokenService;

    public AccessTokenResponse getAccessToken(OAuth2User principal) {
        if (principal == null) {
            throw new UnauthorizedException("認証が必要です");
        }

        String principalName = extractPrincipalName(principal);

        String accessToken = tokenService.getAccessToken(principalName);
        long expiresIn = tokenService.getExpiresIn(principalName);
        String tokenType = tokenService.getTokenType(principalName);

        return new AccessTokenResponse(accessToken, (int) expiresIn, tokenType);
    }

    public UserResponse getUserInfo(OAuth2User principal) {
        if (principal == null) {
            throw new UnauthorizedException("認証が必要です");
        }

        return new UserResponse(
            principal.getAttribute("name"),
            principal.getAttribute("email"),
            principal.getAttribute("preferred_username")
        );
    }

    public AccessTokenResponse refreshAccessToken(OAuth2User principal) {
        if (principal == null) {
            throw new UnauthorizedException("認証が必要です");
        }

        String principalName = extractPrincipalName(principal);
        OAuth2AccessToken accessToken = tokenService.refreshAccessToken(principalName);

        return new AccessTokenResponse(
            accessToken.getTokenValue(),
            (int) tokenService.calculateExpiresIn(accessToken),
            accessToken.getTokenType().getValue()
        );
    }

    /**
     * ユーザーの認証状態を厳密にチェックする
     */
    public boolean isUserAuthenticated(OAuth2User principal, HttpSession session) {
        try {
            // 基本的なnullチェック
            if (principal == null) {
                log.debug("Principal is null");
                return false;
            }

            // セッション存在チェック
            if (session == null) {
                log.debug("Session is null");
                return false;
            }

            // SecurityContextの認証状態チェック
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                log.debug("Authentication is null or not authenticated");
                return false;
            }

            // OAuth2ユーザーの基本属性チェック
            String username = extractPrincipalName(principal);
            if (username == null || username.trim().isEmpty()) {
                log.debug("Username is null or empty");
                return false;
            }

            log.debug("User is authenticated: {}", username);
            return true;

        } catch (Exception e) {
            log.warn("Error checking authentication status", e);
            return false;
        }
    }

    private String extractPrincipalName(OAuth2User principal) {
        String principalName = principal.getAttribute("preferred_username");
        return principalName != null ? principalName : principal.getName();
    }
}