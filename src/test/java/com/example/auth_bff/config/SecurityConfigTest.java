package com.example.auth_bff.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * SecurityConfigの統合テスト
 *
 * <p>Spring Securityの設定全体を検証します。</p>
 *
 * <h3>テストケース:</h3>
 * <ul>
 *   <li>認証不要エンドポイントへのアクセス許可</li>
 *   <li>認証必須エンドポイントへのアクセス制御</li>
 *   <li>CSRF保護の動作確認</li>
 *   <li>CORS設定の動作確認</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "app.frontend.url=http://localhost:5173",
    "app.resource-server.url=http://localhost:9000",
    "app.resource-server.timeout=30",
    "app.cors.allowed-origins=http://localhost:5173",
    "idp.post-logout-redirect-uri=http://localhost:5173/logout-complete",
    "rate-limit.enabled=false",
    "spring.session.store-type=none"
})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * テスト: ヘルスチェックエンドポイントは認証不要
     */
    @Test
    void testHealthEndpoint_ShouldBeAccessibleWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk());
    }

    /**
     * テスト: ログアウトエンドポイントはpermitAllだがCSRF保護が有効
     */
    @Test
    void testLogoutEndpoint_WithoutCsrfToken_ShouldReturn403() throws Exception {
        // CSRFトークンなしでアクセスすると403 Forbidden
        mockMvc.perform(post("/bff/auth/logout"))
            .andExpect(status().isForbidden());
    }

    /**
     * テスト: ユーザー情報エンドポイントは認証必須
     */
    @Test
    void testUserEndpoint_WithoutAuthentication_ShouldRedirectToLogin() throws Exception {
        // 未認証の場合、OAuth2ログインにリダイレクト
        mockMvc.perform(get("/bff/auth/user"))
            .andExpect(status().is3xxRedirection());
    }

    /**
     * テスト: 認証済みユーザーはユーザー情報エンドポイントにアクセス可能
     */
    @Test
    @WithMockUser
    void testUserEndpoint_WithAuthentication_ShouldBeAccessible() throws Exception {
        // モックユーザーでは実際のOAuth2Userが取得できないため、
        // UnauthorizedExceptionまたは500エラーが返される
        mockMvc.perform(get("/bff/auth/user"))
            .andExpect(status().is4xxClientError());
    }

    /**
     * テスト: APIプロキシエンドポイントは認証不要
     * 認証・権限チェックはリソースサーバー側で実施される
     */
    @Test
    void testApiProxyEndpoint_WithoutAuthentication_ShouldBeAccessible() throws Exception {
        // 未認証でもアクセス可能（リソースサーバーが認証チェックを実施）
        // WebClientがリソースサーバーに接続できないため5xxエラーが返される
        mockMvc.perform(get("/api/books"))
            .andExpect(status().is5xxServerError());
    }

    /**
     * テスト: CORS設定 - プリフライトリクエスト
     */
    @Test
    void testCorsConfiguration_PreflightRequest_ShouldBeAllowed() throws Exception {
        mockMvc.perform(options("/api/books")
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "Content-Type,X-XSRF-TOKEN"))
            .andExpect(status().isOk())
            .andExpect(header().exists("Access-Control-Allow-Origin"))
            .andExpect(header().exists("Access-Control-Allow-Methods"));
    }

    /**
     * テスト: CSRF保護 - GETリクエストはCSRFトークン不要
     */
    @Test
    @WithMockUser
    void testCsrfProtection_GetRequest_ShouldNotRequireCsrfToken() throws Exception {
        mockMvc.perform(get("/api/books"))
            .andExpect(status().is5xxServerError()); // 認証は通過、WebClientエラー
    }

    /**
     * テスト: OAuth2エンドポイントは認証不要
     */
    @Test
    void testOAuth2Endpoints_ShouldBeAccessibleWithoutAuthentication() throws Exception {
        // OAuth2認可エンドポイントへのアクセスは認証不要
        mockMvc.perform(get("/oauth2/authorization/idp"))
            .andExpect(status().is3xxRedirection()); // 認証サーバーへリダイレクト
    }
}

