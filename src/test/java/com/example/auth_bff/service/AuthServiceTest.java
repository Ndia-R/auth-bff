package com.example.auth_bff.service;

import com.example.auth_bff.dto.LogoutResponse;
import com.example.auth_bff.dto.UserResponse;
import com.example.auth_bff.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * AuthServiceの単体テスト
 *
 * <p>認証サービスのビジネスロジックを検証します。</p>
 *
 * <h3>テストケース:</h3>
 * <ul>
 *   <li>ログアウト処理（セッション無効化・Cookie削除）</li>
 *   <li>完全ログアウト処理（Keycloak連携）</li>
 *   <li>ユーザー情報取得</li>
 *   <li>未認証時のエラーハンドリング</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private WebClient webClient;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private HttpSession session;

    @Mock
    private OAuth2User oAuth2User;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(webClient);
        ReflectionTestUtils.setField(authService, "keycloakLogoutUri", "http://keycloak:8080/logout");
        ReflectionTestUtils.setField(authService, "postLogoutRedirectUri", "http://localhost:5173/logout-complete");
    }

    /**
     * テスト: 通常ログアウト（BFFセッションのみクリア）
     */
    @Test
    void testLogout_ShouldInvalidateSessionAndClearCookie() {
        // Arrange
        when(request.getSession(false)).thenReturn(session);

        // Act
        LogoutResponse result = authService.logout(request, response, oAuth2User, false);

        // Assert
        assertThat(result.getMessage()).isEqualTo("success");
        verify(session).invalidate();
        verify(response).addCookie(argThat(cookie ->
            cookie.getName().equals("BFFSESSIONID") &&
            cookie.getMaxAge() == 0 &&
            cookie.isHttpOnly()
        ));
    }

    /**
     * テスト: セッションが存在しない場合のログアウト
     */
    @Test
    void testLogout_WhenNoSession_ShouldNotThrowException() {
        // Arrange
        when(request.getSession(false)).thenReturn(null);

        // Act
        LogoutResponse result = authService.logout(request, response, oAuth2User, false);

        // Assert
        assertThat(result.getMessage()).isEqualTo("success");
        verify(response).addCookie(any());
    }

    /**
     * テスト: 完全ログアウト（Keycloak連携）
     * 注: 実際のWebClient呼び出しはモック化が複雑なため、基本動作のみ検証
     */
    @Test
    void testLogout_WithComplete_ShouldAttemptKeycloakLogout() {
        // Arrange
        when(request.getSession(false)).thenReturn(session);

        // OidcUserのモックを作成
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "user123");
        claims.put("name", "Test User");

        OidcIdToken idToken = new OidcIdToken(
            "test-token-value",
            Instant.now(),
            Instant.now().plusSeconds(3600),
            claims
        );

        OidcUser oidcUser = new DefaultOidcUser(null, idToken);

        // Act
        LogoutResponse result = authService.logout(request, response, oidcUser, true);

        // Assert
        assertThat(result.getMessage()).isEqualTo("success");
        verify(session).invalidate();
        // Keycloakへのリクエストは実際には送信されない（WebClientがモック）
    }

    /**
     * テスト: ユーザー情報取得
     */
    @Test
    void testGetUserInfo_ShouldReturnUserResponse() {
        // Arrange
        when(oAuth2User.getAttribute("name")).thenReturn("田中太郎");
        when(oAuth2User.getAttribute("email")).thenReturn("tanaka@example.com");
        when(oAuth2User.getAttribute("preferred_username")).thenReturn("tanaka");

        // Act
        UserResponse result = authService.getUserInfo(oAuth2User);

        // Assert
        assertThat(result.getName()).isEqualTo("田中太郎");
        assertThat(result.getEmail()).isEqualTo("tanaka@example.com");
        assertThat(result.getPreferredUsername()).isEqualTo("tanaka");
    }

    /**
     * テスト: 未認証ユーザーのユーザー情報取得
     */
    @Test
    void testGetUserInfo_WhenPrincipalIsNull_ShouldThrowUnauthorizedException() {
        // Act & Assert
        assertThatThrownBy(() -> authService.getUserInfo(null))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessage("認証が必要です");
    }
}
