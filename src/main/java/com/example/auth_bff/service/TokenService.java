package com.example.auth_bff.service;

import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * TokenService - Spring Security 6対応版
 *
 * 注意: Spring Security 6では@RegisteredOAuth2AuthorizedClientが
 * 自動でトークン管理を行うため、このクラスは最小限の実装となります。
 * 手動リフレッシュロジックは削除し、Spring Securityに委譲します。
 */
@Service
public class TokenService {

    /**
     * OAuth2AccessTokenの有効期限をチェックする
     * Spring Security内部でも使用される基本的なユーティリティ
     */
    public boolean isAccessTokenExpired(OAuth2AccessToken accessToken) {
        if (accessToken == null) {
            return true;
        }
        Instant expiresAt = accessToken.getExpiresAt();
        return expiresAt != null && expiresAt.isBefore(Instant.now());
    }

    /**
     * OAuth2AccessTokenの残り有効時間を秒数で計算する
     */
    public long calculateExpiresIn(OAuth2AccessToken accessToken) {
        if (accessToken == null) {
            return 0;
        }
        Instant expiresAt = accessToken.getExpiresAt();
        if (expiresAt != null) {
            return expiresAt.getEpochSecond() - Instant.now().getEpochSecond();
        }
        return 3600; // デフォルト1時間
    }
}