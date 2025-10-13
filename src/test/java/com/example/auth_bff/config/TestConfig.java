package com.example.auth_bff.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * テスト用設定クラス
 *
 * <p>テスト実行時に必要なBean定義を提供します。</p>
 */
@TestConfiguration
public class TestConfig {

    /**
     * テスト用SecurityFilterChain
     * すべてのリクエストを許可する設定
     */
    @Bean
    @Primary
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(authz -> authz
                .anyRequest().permitAll()
            );
        return http.build();
    }

    /**
     * テスト用WebClient
     * モックとして使用
     */
    @Bean
    @Primary
    public WebClient testWebClient() {
        return WebClient.builder().build();
    }
}