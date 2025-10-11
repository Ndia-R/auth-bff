package com.example.auth_bff.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

import jakarta.servlet.http.HttpServletRequest;

/**
 * APIプロキシコントローラー
 * フロントエンドからのリクエストをリソースサーバーに転送する
 * BFFパターンの実装: トークンをフロントエンドから隠蔽し、サーバー側で管理
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiProxyController {

    private final WebClient webClient;

    @Value("${app.resource-server.url}")
    private String resourceServerUrl;

    // ===========================================
    // 書籍API プロキシ
    // ===========================================

    @GetMapping("/books/**")
    public ResponseEntity<String> proxyBooksGet(
        @RegisteredOAuth2AuthorizedClient("keycloak") OAuth2AuthorizedClient client,
        HttpServletRequest request
    ) {
        return proxyRequest("GET", "/books", client, request, null);
    }

    @PostMapping("/books/**")
    public ResponseEntity<String> proxyBooksPost(
        @RegisteredOAuth2AuthorizedClient("keycloak") OAuth2AuthorizedClient client,
        HttpServletRequest request,
        @RequestBody(required = false) String body
    ) {
        return proxyRequest("POST", "/books", client, request, body);
    }

    @PutMapping("/books/**")
    public ResponseEntity<String> proxyBooksPut(
        @RegisteredOAuth2AuthorizedClient("keycloak") OAuth2AuthorizedClient client,
        HttpServletRequest request,
        @RequestBody(required = false) String body
    ) {
        return proxyRequest("PUT", "/books", client, request, body);
    }

    @DeleteMapping("/books/**")
    public ResponseEntity<String> proxyBooksDelete(
        @RegisteredOAuth2AuthorizedClient("keycloak") OAuth2AuthorizedClient client,
        HttpServletRequest request
    ) {
        return proxyRequest("DELETE", "/books", client, request, null);
    }

    // ===========================================
    // 音楽API プロキシ
    // ===========================================

    @GetMapping("/music/**")
    public ResponseEntity<String> proxyMusicGet(
        @RegisteredOAuth2AuthorizedClient("keycloak") OAuth2AuthorizedClient client,
        HttpServletRequest request
    ) {
        return proxyRequest("GET", "/music", client, request, null);
    }

    @PostMapping("/music/**")
    public ResponseEntity<String> proxyMusicPost(
        @RegisteredOAuth2AuthorizedClient("keycloak") OAuth2AuthorizedClient client,
        HttpServletRequest request,
        @RequestBody(required = false) String body
    ) {
        return proxyRequest("POST", "/music", client, request, body);
    }

    @PutMapping("/music/**")
    public ResponseEntity<String> proxyMusicPut(
        @RegisteredOAuth2AuthorizedClient("keycloak") OAuth2AuthorizedClient client,
        HttpServletRequest request,
        @RequestBody(required = false) String body
    ) {
        return proxyRequest("PUT", "/music", client, request, body);
    }

    @DeleteMapping("/music/**")
    public ResponseEntity<String> proxyMusicDelete(
        @RegisteredOAuth2AuthorizedClient("keycloak") OAuth2AuthorizedClient client,
        HttpServletRequest request
    ) {
        return proxyRequest("DELETE", "/music", client, request, null);
    }

    // ===========================================
    // 共通プロキシ処理
    // ===========================================

    /**
     * リソースサーバーへのプロキシリクエスト
     *
     * @param method HTTPメソッド
     * @param basePath ベースパス (/books, /music)
     * @param client OAuth2認証済みクライアント
     * @param request HTTPリクエスト
     * @param body リクエストボディ（GET/DELETEの場合はnull）
     * @return リソースサーバーからのレスポンス
     */
    private ResponseEntity<String> proxyRequest(
        String method,
        String basePath,
        OAuth2AuthorizedClient client,
        HttpServletRequest request,
        String body
    ) {
        // リクエストパスを抽出（例: /api/books/list → /books/list）
        String path = request.getRequestURI().replace("/api" + basePath, basePath);

        // クエリパラメータを追加
        String queryString = request.getQueryString();
        if (queryString != null && !queryString.isEmpty()) {
            path = path + "?" + queryString;
        }

        // アクセストークンを取得
        String accessToken = client.getAccessToken().getTokenValue();

        log.debug("Proxying {} request to: {}{}", method, resourceServerUrl, path);

        // リソースサーバーにリクエスト転送
        String response = webClient.method(HttpMethod.valueOf(method))
            .uri(resourceServerUrl + path)
            .headers(h -> h.setBearerAuth(accessToken))
            .bodyValue(body != null ? body : "")
            .retrieve()
            .bodyToMono(String.class)
            .block();

        return ResponseEntity.ok(response);
    }
}
