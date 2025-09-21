package com.example.auth_bff.service;

import com.example.auth_bff.dto.AccessTokenResponse;
import com.example.auth_bff.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    private final OAuth2AuthorizedClientService authorizedClientService;
    private final ClientRegistrationRepository clientRegistrationRepository;
    private final WebClient webClient = WebClient.builder().build();

    public OAuth2AuthorizedClient getAuthorizedClient(String principalName) {
        return authorizedClientService.loadAuthorizedClient("keycloak", principalName);
    }

    public String getAccessToken(String principalName) {
        OAuth2AuthorizedClient authorizedClient = getAuthorizedClient(principalName);
        if (authorizedClient != null && authorizedClient.getAccessToken() != null) {
            return authorizedClient.getAccessToken().getTokenValue();
        }
        return null;
    }

    public long getExpiresIn(String principalName) {
        OAuth2AuthorizedClient authorizedClient = getAuthorizedClient(principalName);
        if (authorizedClient != null && authorizedClient.getAccessToken() != null) {
            return calculateExpiresIn(authorizedClient.getAccessToken());
        }
        return 3600; // デフォルト1時間
    }

    public String getTokenType(String principalName) {
        OAuth2AuthorizedClient authorizedClient = getAuthorizedClient(principalName);
        if (authorizedClient != null && authorizedClient.getAccessToken() != null) {
            return authorizedClient.getAccessToken().getTokenType().getValue();
        }
        return "Bearer"; // デフォルト
    }

    public AccessTokenResponse refreshAccessToken(String principalName) {
        OAuth2AuthorizedClient authorizedClient = getAuthorizedClient(principalName);

        if (authorizedClient == null) {
            log.error("認証されたクライアントが見つかりません: {}", principalName);
            throw new UnauthorizedException("認証されたクライアントが見つかりません");
        }

        OAuth2RefreshToken refreshToken = authorizedClient.getRefreshToken();
        if (refreshToken == null) {
            log.error("リフレッシュトークンが見つかりません: {}", principalName);
            throw new UnauthorizedException("リフレッシュトークンが見つかりません");
        }

        try {
            OAuth2AccessTokenResponse tokenResponse = performTokenRefresh(authorizedClient, refreshToken);

            // 新しいトークンでOAuth2AuthorizedClientを更新
            OAuth2AuthorizedClient updatedClient = new OAuth2AuthorizedClient(
                authorizedClient.getClientRegistration(),
                authorizedClient.getPrincipalName(),
                tokenResponse.getAccessToken(),
                tokenResponse.getRefreshToken() != null ? tokenResponse.getRefreshToken() : refreshToken
            );

            authorizedClientService.saveAuthorizedClient(updatedClient, null);

            AccessTokenResponse response = new AccessTokenResponse(
                tokenResponse.getAccessToken().getTokenValue(),
                (int) calculateExpiresIn(tokenResponse.getAccessToken()),
                tokenResponse.getAccessToken().getTokenType().getValue()
            );

            log.info("アクセストークンのリフレッシュが成功しました: {}", principalName);
            return response;

        } catch (Exception e) {
            log.error("トークンリフレッシュ中にエラーが発生しました: {}", e.getMessage(), e);
            throw new UnauthorizedException("トークンリフレッシュに失敗しました: " + e.getMessage());
        }
    }

    private OAuth2AccessTokenResponse performTokenRefresh(OAuth2AuthorizedClient authorizedClient, OAuth2RefreshToken refreshToken) {
        ClientRegistration clientRegistration = authorizedClient.getClientRegistration();

        MultiValueMap<String, String> requestBody = new LinkedMultiValueMap<>();
        requestBody.add("grant_type", "refresh_token");
        requestBody.add("refresh_token", refreshToken.getTokenValue());
        requestBody.add("client_id", clientRegistration.getClientId());
        requestBody.add("client_secret", clientRegistration.getClientSecret());

        return webClient.post()
            .uri(clientRegistration.getProviderDetails().getTokenUri())
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(OAuth2AccessTokenResponse.class)
            .block();
    }

    private long calculateExpiresIn(OAuth2AccessToken accessToken) {
        if (accessToken.getExpiresAt() != null) {
            return accessToken.getExpiresAt().getEpochSecond() - Instant.now().getEpochSecond();
        }
        return 3600; // デフォルト1時間
    }


    public boolean isTokenExpired(String principalName) {
        OAuth2AuthorizedClient authorizedClient = getAuthorizedClient(principalName);
        if (authorizedClient == null || authorizedClient.getAccessToken() == null) {
            return true;
        }

        Instant expiresAt = authorizedClient.getAccessToken().getExpiresAt();
        return expiresAt != null && expiresAt.isBefore(Instant.now());
    }
}