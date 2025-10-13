package com.example.auth_bff.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;

import jakarta.servlet.http.HttpServletRequest;

import java.net.URI;
import java.util.Set;

/**
 * APIプロキシコントローラー
 *
 * <p>フロントエンドからのすべてのAPIリクエストをリソースサーバーに転送する。
 *
 * <h3>BFFパターンの実装</h3>
 * <ul>
 *   <li>トークンをフロントエンドから隠蔽し、BFF側で管理</li>
 *   <li>認証済みユーザーのアクセストークンを自動的に付与</li>
 *   <li>リソースサーバーのレスポンスを透過的に転送</li>
 * </ul>
 *
 * <h3>権限制御</h3>
 * <p>権限制御はリソースサーバー側で行う。BFFは認証済みユーザーのリクエストを
 * そのまま転送し、リソースサーバーが適切に権限チェックを実施する。
 *
 * <h3>WebClient利用</h3>
 * <p>WebClientはWebClientConfigで定義されたシングルトンBeanを使用。
 * コネクションプールの再利用によりパフォーマンスとリソース効率を向上。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class ApiProxyController {

    private final WebClient webClient;

    @Value("${app.resource-server.url}")
    private String resourceServerUrl;

    /**
     * 除外すべきレスポンスヘッダー
     * これらはSpringが自動設定するため、リソースサーバーからコピーしない
     */
    private static final Set<String> EXCLUDED_RESPONSE_HEADERS = Set.of(
        "transfer-encoding",
        "connection",
        "keep-alive",
        "upgrade",
        "server",
        "content-length"
    );

    /**
     * すべてのAPIリクエストをリソースサーバーにプロキシ
     *
     * <p>このエンドポイントは /api/** 配下のすべてのリクエストを受け付け、
     * 認証済みユーザーのアクセストークンを付与してリソースサーバーに転送する。
     *
     * <h3>処理フロー</h3>
     * <ol>
     *   <li>リクエストパスからHTTPメソッドとパスを取得</li>
     *   <li>UriBuilderで安全なURIを構築（クエリパラメータ自動エンコード）</li>
     *   <li>アクセストークンとContent-Typeヘッダーを設定</li>
     *   <li>リソースサーバーにリクエストを転送（30秒タイムアウト）</li>
     *   <li>レスポンスのステータスコード・ヘッダー・ボディを保持して返却</li>
     * </ol>
     *
     * <h3>権限制御</h3>
     * <p>権限制御はリソースサーバー側で行われる。BFFは認証されたユーザーの
     * リクエストを転送するのみで、エンドポイントごとの権限チェックは行わない。
     *
     * @param client OAuth2認証済みクライアント（アクセストークンを含む）
     * @param request HTTPリクエスト
     * @param body リクエストボディ（GET/DELETEの場合はnull）
     * @return リソースサーバーからのレスポンス（ステータスコード・ヘッダー・ボディ）
     */
    @RequestMapping("/**")
    public ResponseEntity<String> proxyAll(
        @RegisteredOAuth2AuthorizedClient("keycloak") OAuth2AuthorizedClient client,
        HttpServletRequest request,
        @RequestBody(required = false) String body
    ) {
        // リクエスト情報を取得
        String method = request.getMethod();
        String path = request.getRequestURI().replace("/api", "");

        log.debug("Proxying {} request to: {}{}", method, resourceServerUrl, path);

        // WebClientリクエストビルダー
        WebClient.RequestBodyUriSpec requestBuilder = webClient.method(HttpMethod.valueOf(method));

        // URI設定（共通）
        WebClient.RequestBodySpec bodySpec = requestBuilder.uri(uriBuilder -> {
            URI baseUri = URI.create(resourceServerUrl);
            UriBuilder builder = uriBuilder
                .scheme(baseUri.getScheme())
                .host(baseUri.getHost())
                .port(baseUri.getPort())
                .path(path);

            // クエリパラメータを追加（自動エンコード）
            request.getParameterMap()
                .forEach((key, values) -> builder.queryParam(key, (Object[]) values));

            return builder.build();
        });

        // ボディ設定 + ヘッダー設定（POST/PUTの場合はボディを先に設定）
        WebClient.RequestHeadersSpec<?> headersSpec;

        if ("GET".equals(method) || "DELETE".equals(method)) {
            // GET/DELETE: ヘッダー設定のみ
            headersSpec = bodySpec
                .headers(h -> h.setBearerAuth(client.getAccessToken().getTokenValue()));
        } else {
            // POST/PUT: ボディ設定 → ヘッダー設定（Content-Type含む）
            headersSpec = bodySpec
                .bodyValue(body != null ? body : "")
                .headers(h -> {
                    h.setBearerAuth(client.getAccessToken().getTokenValue());
                    // リクエストのContent-Typeをリソースサーバーに転送
                    String contentType = request.getContentType();
                    if (contentType != null) {
                        h.setContentType(MediaType.parseMediaType(contentType));
                    }
                });
        }

        // リクエスト実行（ステータスコード・ヘッダー・ボディをすべて保持）
        return headersSpec
            .exchangeToMono(response -> {
                // ステータスコードを保持
                HttpStatusCode statusCode = response.statusCode();

                // レスポンスヘッダーをフィルタリング
                HttpHeaders originalHeaders = response.headers().asHttpHeaders();
                HttpHeaders filteredHeaders = new HttpHeaders();
                originalHeaders.forEach((name, values) -> {
                    if (!EXCLUDED_RESPONSE_HEADERS.contains(name.toLowerCase())) {
                        filteredHeaders.addAll(name, values);
                    }
                });

                // ボディを取得してレスポンスを構築
                return response.bodyToMono(String.class)
                    .map(
                        responseBody -> ResponseEntity
                            .status(statusCode)
                            .headers(filteredHeaders)
                            .body(responseBody)
                    );
            })
            .block();
    }
}
