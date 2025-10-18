package com.example.auth_bff.config;

import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.lettuce.core.RedisClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.reactive.function.client.WebClient;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    /**
     * テスト用RedisClient
     * モックとして使用（実際のRedis接続は不要）
     */
    @Bean
    @Primary
    public RedisClient testRedisClient() {
        // Mockitoでモック化（実際のRedis接続を回避）
        return mock(RedisClient.class);
    }

    /**
     * テスト用ProxyManager
     * モックとして使用（レート制限テストでは使用しない）
     *
     * <p>注意: rate-limit.enabled=falseの場合、このBeanは使用されません。
     * ただし、一部のテストでrate-limit.enabled=trueの可能性があるため、
     * 適切にモック化しておく必要があります。</p>
     */
    @Bean
    @Primary
    @SuppressWarnings("unchecked")
    public ProxyManager<byte[]> testProxyManager() {
        // ProxyManagerをモック化
        ProxyManager<byte[]> mock = mock(ProxyManager.class);

        // builder()メソッドが呼ばれた場合のモック動作を設定
        // (実際には使用されないが、念のため設定)
        when(mock.builder()).thenReturn(null);

        return mock;
    }
}