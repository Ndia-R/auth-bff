package com.example.auth_bff.service;

import com.example.auth_bff.dto.LogoutResponse;
import com.example.auth_bff.dto.UserResponse;
import com.example.auth_bff.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
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

    private final WebClient webClient;

    @Value("${keycloak.logout-uri}")
    private String keycloakLogoutUri;

    @Value("${keycloak.post-logout-redirect-uri}")
    private String postLogoutRedirectUri;

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
        // BFFセッション関連のクリア
        invalidateSession(request);
        clearSecurityContext();
        clearSessionCookie(response);

        // Keycloakログアウト処理（完全ログアウト時のみ）
        if (complete) {
            processKeycloakLogout(principal);
        }

        return new LogoutResponse("success");
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

    // ===========================================
    // プライベートメソッド（処理順）
    // ===========================================

    /**
     * セッションを無効化する
     */
    private void invalidateSession(HttpServletRequest request) {
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
            // OidcUserでない場合はスキップ
            if (!(principal instanceof OidcUser)) {
                log.debug("Principal is not an OidcUser, skipping Keycloak logout");
                return;
            }

            OidcUser oidcUser = (OidcUser) principal;
            String idToken = oidcUser.getIdToken().getTokenValue();
            if (idToken == null) {
                log.debug("No ID token found, skipping Keycloak logout");
                return;
            }

            // OpenID Connect End Session Endpointを使用してログアウトURLを構築
            String endSessionUrl = UriComponentsBuilder
                .fromUriString(keycloakLogoutUri)
                .queryParam("id_token_hint", idToken)
                .queryParam("post_logout_redirect_uri", postLogoutRedirectUri)
                .build()
                .toUriString();

            log.debug("Initiating Keycloak logout");

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

            log.info("Keycloak logout completed");

        } catch (WebClientResponseException e) {
            log.warn("Keycloak logout failed ({}), but BFF logout will continue", e.getStatusCode());

        } catch (WebClientException e) {
            log.warn("Could not connect to Keycloak for logout, but BFF logout will continue");

        } catch (Exception e) {
            log.warn("Keycloak logout error: {}, but BFF logout will continue", e.getMessage());
        }
    }

}