package com.example.auth_bff.service;

import com.example.auth_bff.dto.AccessTokenResponse;
import com.example.auth_bff.dto.LogoutResponse;
import com.example.auth_bff.dto.UserResponse;
import com.example.auth_bff.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

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
            processKeycloakLogout(principal);
        }

        return new LogoutResponse("success");
    }

    /**
     * アクセストークンを取得する
     */
    public AccessTokenResponse getAccessToken(OAuth2User principal, OAuth2AuthorizedClient authorizedClient) {
        if (principal == null) {
            throw new UnauthorizedException("認証が必要です");
        }
        if (authorizedClient == null) {
            throw new UnauthorizedException("認証されたクライアントが見つかりません");
        }

        // Spring Securityが自動でリフレッシュしたトークンを使用
        OAuth2AccessToken accessToken = authorizedClient.getAccessToken();
        String tokenValue = accessToken.getTokenValue();
        long expiresIn = tokenService.calculateExpiresIn(accessToken);
        String tokenType = accessToken.getTokenType().getValue();

        return new AccessTokenResponse(tokenValue, (int) expiresIn, tokenType);
    }

    /**
     * アクセストークンをリフレッシュする
     * 注意: Spring Security 6では@RegisteredOAuth2AuthorizedClientが自動でリフレッシュを処理するため、
     * このメソッドは実質的にgetAccessTokenと同じ動作になります
     */
    public AccessTokenResponse refreshAccessToken(OAuth2User principal, OAuth2AuthorizedClient authorizedClient) {
        if (principal == null) {
            throw new UnauthorizedException("認証が必要です");
        }
        if (authorizedClient == null) {
            throw new UnauthorizedException("認証されたクライアントが見つかりません");
        }

        // Spring Securityが既にリフレッシュ済みのトークンを提供
        OAuth2AccessToken accessToken = authorizedClient.getAccessToken();
        String tokenValue = accessToken.getTokenValue();
        long expiresIn = tokenService.calculateExpiresIn(accessToken);
        String tokenType = accessToken.getTokenType().getValue();

        return new AccessTokenResponse(tokenValue, (int) expiresIn, tokenType);
    }

    /**
     * ユーザー情報を取得する
     */
    public UserResponse getUserInfo(OAuth2User principal) {
        if (principal == null) {
            throw new UnauthorizedException("認証が必要です");
        }

        String name = principal.getAttribute("name");
        String email = principal.getAttribute("email");
        String preferredUsername = principal.getAttribute("preferred_username");

        return new UserResponse(name, email, preferredUsername);
    }


    /**
     * ユーザーの認証状態を包括的にチェックする（セッション + トークン有効期限）
     */
    public boolean isUserFullyAuthenticated(OAuth2User principal, HttpSession session, OAuth2AuthorizedClient authorizedClient) {
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

            // OAuth2AuthorizedClientとトークン有効期限チェック
            if (authorizedClient == null || authorizedClient.getAccessToken() == null) {
                log.debug("Authorized client or access token is null: {}", username);
                return false;
            }

            // アクセストークンの有効期限チェック
            OAuth2AccessToken accessToken = authorizedClient.getAccessToken();
            if (tokenService.isAccessTokenExpired(accessToken)) {
                log.debug("Access token is expired: {}", username);
                return false;
            }

            log.debug("User is authenticated: {}", username);
            return true;

        } catch (Exception e) {
            log.warn("Error checking authentication status", e);
            return false;
        }
    }


    /**
     * OAuth2ユーザーから識別名を抽出する
     */
    public String extractPrincipalName(OAuth2User principal) {
        String principalName = principal.getAttribute("preferred_username");
        return principalName != null ? principalName : principal.getName();
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
        }
    }

    /**
     * セキュリティコンテキストをクリアする
     */
    private void clearSecurityContext() {
        SecurityContextHolder.clearContext();
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
    }

    /**
     * Keycloakログアウト処理を実行する（OpenID Connect RP-Initiated Logout方式）
     * @param principal 認証済みユーザー情報
     */
    private void processKeycloakLogout(OAuth2User principal) {
        try {
            String principalName = extractPrincipalName(principal);

            // OidcUserでない場合はスキップ
            if (!(principal instanceof OidcUser)) {
                log.debug("Principal is not an OidcUser, skipping Keycloak logout for user: {}", principalName);
                return;
            }

            OidcUser oidcUser = (OidcUser) principal;
            String idToken = oidcUser.getIdToken().getTokenValue();
            if (idToken == null) {
                log.debug("No ID token found for user: {}, skipping Keycloak logout", principalName);
                return;
            }

            // OpenID Connect End Session Endpointを使用してログアウトURLを構築
            String endSessionUrl = UriComponentsBuilder
                .fromUriString(keycloakLogoutUri)
                .queryParam("id_token_hint", idToken)
                .build()
                .toUriString();

            // GETリクエストでKeycloakログアウト（OpenID Connect仕様準拠）
            webClient.get()
                .uri(endSessionUrl)
                .exchangeToMono(clientResponse -> {
                    if (clientResponse.statusCode().is2xxSuccessful() ||
                        clientResponse.statusCode().is3xxRedirection()) {
                        return clientResponse.bodyToMono(String.class);
                    } else {
                        log.error("Keycloak logout failed with status: {}", clientResponse.statusCode());
                        return clientResponse.bodyToMono(String.class)
                            .then(
                                Mono.error(
                                    new RuntimeException("Keycloak logout failed with " + clientResponse.statusCode())
                                )
                            );
                    }
                })
                .block();

            log.debug("Keycloak logout successful for user: {}", principalName);

        } catch (WebClientResponseException e) {
            log.warn("Keycloak logout failed ({}), but BFF logout will continue", e.getStatusCode());

        } catch (WebClientException e) {
            log.warn("Could not connect to Keycloak for logout, but BFF logout will continue");

        } catch (Exception e) {
            log.warn("Keycloak logout error: {}, but BFF logout will continue", e.getMessage());
        }
    }

}