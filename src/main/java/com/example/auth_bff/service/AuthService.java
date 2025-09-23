package com.example.auth_bff.service;

import com.example.auth_bff.dto.AccessTokenResponse;
import com.example.auth_bff.dto.LogoutResponse;
import com.example.auth_bff.dto.UserResponse;
import com.example.auth_bff.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final TokenService tokenService;
    private final WebClient webClient;

    @Value("${keycloak.logout-uri}")
    private String keycloakLogoutUri;

    // ===========================================
    // 公開メソッド（エントリーポイント）
    // ===========================================

    /**
     * ログアウト
     * @param request HTTPリクエスト
     * @param response HTTPレスポンス
     * @param principal 認証済みユーザー情報
     * @param complete 完全ログアウト（Keycloakセッションも無効化）するかどうか
     * @return ログアウト情報
     */
    public LogoutResponse logout(
        HttpServletRequest request,
        HttpServletResponse response,
        OAuth2User principal,
        boolean complete
    ) {
        String username = (principal != null) ? principal.getName() : "anonymous";

        // BFFセッション関連のクリア
        invalidateSession(request, username);
        clearSecurityContext();
        clearSessionCookie(response);

        // Keycloakログアウト処理（完全ログアウト時のみ）
        if (complete) {
            processKeycloakLogout();
        }

        log.info("Logout completed. Complete: {}, User: {}", complete, username);

        return new LogoutResponse("success");
    }

    /**
     * アクセストークンを取得する
     */
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

    /**
     * ユーザー情報を取得する
     */
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

    /**
     * アクセストークンをリフレッシュする
     */
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

    // ===========================================
    // プライベートメソッド（処理順）
    // ===========================================

    /**
     * セッションを無効化する
     */
    private void invalidateSession(HttpServletRequest request, String username) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
            log.debug("Session invalidated for user: {}", username);
        } else {
            log.debug("No active session found for user: {}", username);
        }
    }

    /**
     * セキュリティコンテキストをクリアする
     */
    private void clearSecurityContext() {
        SecurityContextHolder.clearContext();
        log.debug("Security context cleared");
    }

    /**
     * セッションクッキーを削除する
     */
    private void clearSessionCookie(HttpServletResponse response) {
        Cookie bffSessionCookie = new Cookie("BFFSESSIONID", null);
        bffSessionCookie.setPath("/");
        bffSessionCookie.setHttpOnly(true);
        bffSessionCookie.setMaxAge(0);
        response.addCookie(bffSessionCookie);
        log.debug("Session cookie cleared");
    }

    /**
    * Keycloakログアウト処理を実行する
    */
    private void processKeycloakLogout() {
        try {
            // WebClientでKeycloakログアウトエンドポイントを呼び出し
            String response = webClient.get()
                .uri(keycloakLogoutUri)
                .retrieve()
                .bodyToMono(String.class)
                .block(); // 同期処理に変換

            log.info("Keycloak logout completed successfully");
            log.debug("Successfully called Keycloak logout endpoint: {}", keycloakLogoutUri);
            log.trace("Keycloak logout response: {}", response);
        } catch (Exception e) {
            log.warn("Failed to call Keycloak logout endpoint: {}", e.getMessage());
            log.error("Error calling Keycloak logout endpoint: {}", keycloakLogoutUri, e);
            // Keycloakログアウトが失敗してもBFFログアウトは継続
        }
    }

    /**
     * OAuth2ユーザーから識別名を抽出する
     */
    private String extractPrincipalName(OAuth2User principal) {
        String principalName = principal.getAttribute("preferred_username");
        return principalName != null ? principalName : principal.getName();
    }
}