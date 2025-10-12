package com.example.auth_bff.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.AuthenticatedPrincipalOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;

/**
 * Spring Security設定クラス
 *
 * <p>このBFF (Backend for Frontend) アプリケーションのセキュリティ設定を管理します。</p>
 *
 * <h3>主な責務:</h3>
 * <ul>
 *   <li>OAuth2 + PKCE認証フローの設定</li>
 *   <li>CSRF保護（CookieベースのCSRFトークン）</li>
 *   <li>CORS設定（フロントエンドからのクロスオリジンリクエスト許可）</li>
 *   <li>認可ルール（どのエンドポイントが認証不要/認証必須か）</li>
 *   <li>OAuth2クライアント情報の保存先設定（Redisセッション連携）</li>
 * </ul>
 *
 * <h3>セキュリティ設計:</h3>
 * <ul>
 *   <li><b>BFFパターン</b>: トークンはBFF側で完全管理、フロントエンドはセッションCookieのみ使用</li>
 *   <li><b>PKCE対応</b>: Authorization Code Flowの認可コード盗聴攻撃を防止</li>
 *   <li><b>CSRF保護</b>: 状態変更操作（POST/PUT/DELETE）を保護</li>
 *   <li><b>HttpOnly Cookie</b>: セッションCookieへのJavaScriptアクセスを防止（XSS対策）</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * フロントエンドアプリケーションのURL
     * OAuth2認証成功後のリダイレクト先として使用
     */
    @Value("${app.frontend.url}")
    private String frontendUrl;

    /**
     * CORS許可オリジン（カンマ区切り）
     * 例: "http://localhost:5173,http://localhost:*"
     */
    @Value("${app.cors.allowed-origins}")
    private String corsAllowedOrigins;

    /**
     * Spring Securityのフィルターチェーン設定
     *
     * <p>このメソッドはSpring Securityの中核となる設定で、以下の順序で処理されます：</p>
     * <ol>
     *   <li><b>CORS処理</b>: クロスオリジンリクエストの検証</li>
     *   <li><b>CSRF処理</b>: CSRFトークンの検証とCookie設定</li>
     *   <li><b>認証チェック</b>: セッションCookieからユーザー情報を取得</li>
     *   <li><b>認可チェック</b>: エンドポイントへのアクセス権限確認</li>
     *   <li><b>OAuth2処理</b>: 未認証の場合、Keycloakへリダイレクト</li>
     * </ol>
     *
     * @param http Spring SecurityのHttpSecurity設定オブジェクト
     * @param pkceResolver PKCE対応のOAuth2認可リクエストリゾルバ
     * @return 構築されたSecurityFilterChain
     * @throws Exception 設定エラー時
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, OAuth2AuthorizationRequestResolver pkceResolver)
        throws Exception {
        http
            // ═══════════════════════════════════════════════════════════════
            // CORS設定: フロントエンドからのクロスオリジンリクエストを許可
            // ═══════════════════════════════════════════════════════════════
            .cors(
                cors -> cors.configurationSource(corsConfigurationSource())
            )
            // ═══════════════════════════════════════════════════════════════
            // CSRF保護設定: POST/PUT/DELETE等の状態変更操作を保護
            // ═══════════════════════════════════════════════════════════════
            .csrf(
                csrf -> csrf
                    // CSRFトークンをCookieに保存（HttpOnly=false: JavaScriptから読み取り可能）
                    // フロントエンドは X-XSRF-TOKEN ヘッダーにトークンを設定して送信
                    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    // CSRFトークンをリクエスト属性として利用可能にする
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
            )
            // CSRFトークンを確実にCookieに設定するカスタムフィルターを追加
            // Spring SecurityのCsrfFilterの直後に実行され、XSRF-TOKEN Cookieを生成
            .addFilterAfter(
                new CsrfCookieFilter(),
                CsrfFilter.class
            )
            // ═══════════════════════════════════════════════════════════════
            // 認可設定: エンドポイントごとのアクセス制御
            // ═══════════════════════════════════════════════════════════════
            .authorizeHttpRequests(
                authz -> authz
                    // ────────────────────────────────────────────────────
                    // 認証不要エンドポイント（permitAll）
                    // ────────────────────────────────────────────────────
                    .requestMatchers(
                        // ヘルスチェック: Kubernetes等の監視システムからアクセス
                        "/actuator/health",

                        // ログアウト: セッション無効化後も200 OKを返すため認証不要
                        // ※実際の処理はAuthControllerで認証状態を確認
                        "/bff/auth/logout",

                        // OAuth2認証開始: Spring Security標準のエンドポイント
                        // /oauth2/authorization/keycloak → Keycloakへリダイレクト
                        "/oauth2/**",

                        // OAuth2コールバック: Keycloakからの認可コード受け取り
                        // /bff/login/oauth2/code/keycloak?code=xxx&state=xxx
                        "/bff/login/oauth2/**",

                        // OpenID Connect Discovery: OAuth2プロバイダーのメタデータ
                        "/.well-known/**"
                    )
                    .permitAll()

                    // ────────────────────────────────────────────────────
                    // 上記以外のすべてのリクエストは認証必須
                    // ────────────────────────────────────────────────────
                    // /bff/auth/user, /bff/auth/login, /api/** など
                    .anyRequest()
                    .authenticated()
            )
            // ═══════════════════════════════════════════════════════════════
            // OAuth2ログイン設定: Keycloakとの認証フロー
            // ═══════════════════════════════════════════════════════════════
            .oauth2Login(
                oauth2 -> oauth2
                    // 認可エンドポイント設定: PKCE対応のリゾルバを使用
                    // code_challenge/code_verifierを自動生成
                    .authorizationEndpoint(authz -> authz.authorizationRequestResolver(pkceResolver))

                    // リダイレクションエンドポイント: Keycloakからのコールバックを受け取るパス
                    // 例: /bff/login/oauth2/code/keycloak
                    .redirectionEndpoint(redirection -> redirection.baseUri("/bff/login/oauth2/code/*"))

                    // 認証成功ハンドラー: OAuth2認証完了後にフロントエンドへリダイレクト
                    .successHandler(authenticationSuccessHandler())
            )
            // ═══════════════════════════════════════════════════════════════
            // ログアウト設定: カスタムエンドポイントで処理
            // ═══════════════════════════════════════════════════════════════
            // Spring Security標準のログアウト機能（/logout）は使用しない
            // 理由: 通常ログアウト/完全ログアウトの2種類を実装するため
            // 実際の処理は AuthController.logout() で実装
            .logout(
                logout -> logout.disable()
            );

        return http.build();
    }

    /**
     * OAuth2認証成功後のカスタムハンドラー
     *
     * <p>Keycloakでの認証完了後、このハンドラーがフロントエンドにリダイレクトします。</p>
     *
     * <h3>処理フロー:</h3>
     * <ol>
     *   <li>Keycloakからのコールバック受信（認可コード取得）</li>
     *   <li>Spring Securityがトークン交換を自動実行</li>
     *   <li>トークンをRedisセッションに保存</li>
     *   <li><b>このハンドラーが実行</b> → フロントエンドにリダイレクト</li>
     * </ol>
     *
     * <h3>リダイレクト先の決定:</h3>
     * <ul>
     *   <li>デフォルト: <code>${frontendUrl}/auth-callback</code></li>
     *   <li>continueパラメータがある場合: そのURLを使用</li>
     * </ul>
     *
     * @return OAuth2認証成功ハンドラー
     */
    @Bean
    public AuthenticationSuccessHandler authenticationSuccessHandler() {
        return (request, response, authentication) -> {
            // デフォルトのリダイレクト先: フロントエンドの認証コールバックページ
            String redirectUrl = frontendUrl + "/auth-callback";

            // continueパラメータがある場合はそれを優先
            // 例: /oauth2/authorization/keycloak?continue=/dashboard
            // → 認証後に /dashboard へリダイレクト
            String continueParam = request.getParameter("continue");
            if (continueParam != null && !continueParam.isEmpty()) {
                redirectUrl = continueParam;
            }

            // フロントエンドにリダイレクト
            // CSRFトークン（XSRF-TOKEN Cookie）はCsrfCookieFilterで自動設定済み
            response.sendRedirect(redirectUrl);
        };
    }

    /**
     * OAuth2認可クライアント情報の保存先設定
     *
     * <p>OAuth2トークン（アクセストークン、リフレッシュトークン等）の保存先を設定します。</p>
     *
     * <h3>保存される情報:</h3>
     * <ul>
     *   <li>アクセストークン（Resource Serverへのアクセスに使用）</li>
     *   <li>リフレッシュトークン（アクセストークン更新に使用）</li>
     *   <li>IDトークン（ユーザー情報、完全ログアウトに使用）</li>
     *   <li>トークンの有効期限情報</li>
     * </ul>
     *
     * <h3>保存先:</h3>
     * <ul>
     *   <li><b>Spring Session + Redis</b>: セッション情報として一元管理</li>
     *   <li>RedisConfigで設定されたRedis接続を使用</li>
     *   <li>セッションタイムアウト: 30分（application.yml）</li>
     * </ul>
     *
     * <h3>認証済みユーザーとの紐付け:</h3>
     * <p><code>AuthenticatedPrincipalOAuth2AuthorizedClientRepository</code>により、
     * 現在ログイン中のユーザー（OAuth2User）に紐付けてトークンを保存します。</p>
     *
     * @param clientRegistrationRepository OAuth2クライアント登録情報（application.yml）
     * @param authorizedClientService OAuth2認可クライアントサービス
     * @return OAuth2認可クライアント情報のリポジトリ
     */
    @Bean
    public OAuth2AuthorizedClientRepository authorizedClientRepository(
        ClientRegistrationRepository clientRegistrationRepository,
        OAuth2AuthorizedClientService authorizedClientService
    ) {
        return new AuthenticatedPrincipalOAuth2AuthorizedClientRepository(authorizedClientService);
    }

    /**
     * CORS（Cross-Origin Resource Sharing）設定
     *
     * <p>フロントエンドアプリケーション（別オリジン）からのリクエストを許可します。</p>
     *
     * <h3>開発環境例:</h3>
     * <ul>
     *   <li>フロントエンド: <code>http://localhost:5173</code> (Vite)</li>
     *   <li>BFF: <code>http://localhost:8888</code></li>
     *   <li>→ オリジンが異なるためCORS設定が必要</li>
     * </ul>
     *
     * <h3>設定内容:</h3>
     * <ul>
     *   <li><b>許可オリジン</b>: 環境変数 <code>CORS_ALLOWED_ORIGINS</code> から読み込み</li>
     *   <li><b>許可メソッド</b>: GET, POST, PUT, DELETE, OPTIONS</li>
     *   <li><b>許可ヘッダー</b>: Authorization, Content-Type, X-XSRF-TOKEN</li>
     *   <li><b>資格情報</b>: 許可（Cookie送信を有効化）</li>
     * </ul>
     *
     * @return CORS設定ソース
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // ────────────────────────────────────────────────────
        // 許可オリジンの設定
        // ────────────────────────────────────────────────────
        // 環境変数から読み込み（カンマ区切り）
        // 例: "http://localhost:5173,http://localhost:*"
        String[] allowedOrigins = corsAllowedOrigins.split(",");
        configuration.setAllowedOriginPatterns(Arrays.asList(allowedOrigins));

        // ────────────────────────────────────────────────────
        // 許可HTTPメソッドの設定
        // ────────────────────────────────────────────────────
        // GET: データ取得、POST: データ作成、PUT: データ更新、DELETE: データ削除
        // OPTIONS: プリフライトリクエスト（CORS事前確認）
        configuration.setAllowedMethods(
            Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS")
        );

        // ────────────────────────────────────────────────────
        // 許可HTTPヘッダーの設定
        // ────────────────────────────────────────────────────
        // Authorization: トークン送信（将来的なAPI直接アクセス用）
        // Content-Type: リクエストボディの形式指定
        // X-XSRF-TOKEN: CSRF保護用トークン（POST/PUT/DELETE時に必須）
        configuration.setAllowedHeaders(
            Arrays.asList("Authorization", "Content-Type", "X-XSRF-TOKEN")
        );

        // ────────────────────────────────────────────────────
        // 資格情報（Cookie）の送信を許可
        // ────────────────────────────────────────────────────
        // true: フロントエンドからのリクエストにCookieを含めることを許可
        // BFFセッションCookie（BFFSESSIONID）の送信に必須
        configuration.setAllowCredentials(true);

        // すべてのパス（/**）に対してこのCORS設定を適用
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * PKCE対応のOAuth2認可リクエストリゾルバ
     *
     * <p>PKCE (Proof Key for Code Exchange) は、Authorization Code Flowの
     * セキュリティを強化するための仕組みです。</p>
     *
     * <h3>PKCEの仕組み:</h3>
     * <ol>
     *   <li><b>code_verifier生成</b>: ランダムな文字列を生成（43-128文字）</li>
     *   <li><b>code_challenge生成</b>: <code>BASE64URL(SHA256(code_verifier))</code></li>
     *   <li><b>認可リクエスト</b>: code_challengeをKeycloakに送信</li>
     *   <li><b>トークン交換</b>: code_verifierをKeycloakに送信して検証</li>
     * </ol>
     *
     * <h3>防止できる攻撃:</h3>
     * <ul>
     *   <li><b>認可コード盗聴攻撃</b>: 認可コードを盗んでも、code_verifierがないとトークン取得不可</li>
     *   <li><b>リダイレクトURI改ざん</b>: 正規のアプリケーションのみがトークン取得可能</li>
     * </ul>
     *
     * <h3>生成されるパラメータ:</h3>
     * <pre>
     * 認可リクエスト:
     *   code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM
     *   code_challenge_method=S256
     *
     * トークン交換リクエスト:
     *   code_verifier=dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk
     * </pre>
     *
     * @param clientRegistrationRepository OAuth2クライアント登録情報
     * @return PKCE対応のOAuth2認可リクエストリゾルバ
     */
    @Bean
    public OAuth2AuthorizationRequestResolver pkceResolver(ClientRegistrationRepository clientRegistrationRepository) {
        // デフォルトのリゾルバを作成
        // 第2引数: OAuth2認証を開始するパス（/oauth2/authorization/{registrationId}）
        DefaultOAuth2AuthorizationRequestResolver resolver = new DefaultOAuth2AuthorizationRequestResolver(
            clientRegistrationRepository,
            "/oauth2/authorization"
        );

        // PKCEカスタマイザーを適用
        // code_challengeとcode_verifierを自動生成し、認可リクエストに追加
        resolver.setAuthorizationRequestCustomizer(
            OAuth2AuthorizationRequestCustomizers.withPkce()
        );

        return resolver;
    }

}