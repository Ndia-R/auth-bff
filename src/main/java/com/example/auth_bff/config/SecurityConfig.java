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

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Value("${app.cors.allowed-origins}")
    private String corsAllowedOrigins;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, OAuth2AuthorizationRequestResolver pkceResolver)
        throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(
                csrf -> csrf
                    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
            )
            // CSRFトークンを確実にCookieに設定するフィルターを追加
            .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
            .authorizeHttpRequests(
                authz -> authz
                    .requestMatchers(
                        "/actuator/health", // ヘルスチェック - 監視システムからアクセス
                        "/bff/auth/logout", // ログアウト - セッション無効化後も正常レスポンス
                        "/oauth2/**", // OAuth2認証開始 - Spring Security標準パス
                        "/bff/login/oauth2/**", // Keycloakコールバック - OAuth2認証完了後のリダイレクト
                        "/.well-known/**" // OpenID Connect設定情報 - メタデータエンドポイント
                    )
                    .permitAll()
                    .anyRequest()
                    .authenticated()
            )
            .oauth2Login(
                oauth2 -> oauth2
                    .authorizationEndpoint(
                        authz -> authz
                            .authorizationRequestResolver(pkceResolver)
                    )
                    .redirectionEndpoint(
                        redirection -> redirection
                            .baseUri("/bff/login/oauth2/code/*")
                    )
                    .successHandler(authenticationSuccessHandler())
            )
            // ログアウトはカスタムエンドポイント(/bff/auth/logout)で処理するため
            // Spring Security標準のログアウト機能は無効化
            .logout(logout -> logout.disable());

        return http.build();
    }

    @Bean
    public AuthenticationSuccessHandler authenticationSuccessHandler() {
        return (request, response, authentication) -> {
            // OAuth2認証成功後、フロントエンドにリダイレクト
            // CSRFトークンはCsrfCookieFilterで自動的に設定される
            String redirectUrl = frontendUrl + "/auth-callback";

            // continueパラメータがある場合はそれを使用
            String continueParam = request.getParameter("continue");
            if (continueParam != null && !continueParam.isEmpty()) {
                redirectUrl = continueParam;
            }

            response.sendRedirect(redirectUrl);
        };
    }

    @Bean
    public OAuth2AuthorizedClientRepository authorizedClientRepository(
        ClientRegistrationRepository clientRegistrationRepository,
        OAuth2AuthorizedClientService authorizedClientService
    ) {
        return new AuthenticatedPrincipalOAuth2AuthorizedClientRepository(authorizedClientService);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // 環境変数から許可オリジンを読み込み
        String[] allowedOrigins = corsAllowedOrigins.split(",");
        configuration.setAllowedOriginPatterns(Arrays.asList(allowedOrigins));

        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        // 必要なヘッダーのみ許可
        configuration.setAllowedHeaders(
            Arrays.asList(
                "Authorization",
                "Content-Type",
                "X-XSRF-TOKEN"
            )
        );

        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public OAuth2AuthorizationRequestResolver pkceResolver(ClientRegistrationRepository clientRegistrationRepository) {
        DefaultOAuth2AuthorizationRequestResolver authorizationRequestResolver = new DefaultOAuth2AuthorizationRequestResolver(
            clientRegistrationRepository,
            "/oauth2/authorization"
        );
        authorizationRequestResolver.setAuthorizationRequestCustomizer(
            OAuth2AuthorizationRequestCustomizers.withPkce()
        );
        return authorizationRequestResolver;
    }

}