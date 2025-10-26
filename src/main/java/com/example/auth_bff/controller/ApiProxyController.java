package com.example.auth_bff.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
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
 *   <li>未認証ユーザーのリクエストはトークンなしでリソースサーバーへ転送</li>
 *   <li>リソースサーバーのレスポンスを透過的に転送</li>
 * </ul>
 *
 * <h3>権限制御</h3>
 * <p>権限制御はリソースサーバー側で行う。BFFは認証・未認証に関わらず
 * すべてのリクエストを転送し、リソースサーバーが適切に権限チェックを実施する。
 * 認証が必要なエンドポイントにアクセスした場合、リソースサーバーが401を返す。
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
    private final OAuth2AuthorizedClientRepository authorizedClientRepository;

    @Value("${app.resource-server.url}")
    private String resourceServerUrl;

    /**
     * 除外すべきレスポンスヘッダー
     *
     * <p>これらのヘッダーはSpring Bootが自動的に設定するため、
     * リソースサーバーからのレスポンスヘッダーをそのままコピーすると
     * 重複や競合が発生する可能性があります。</p>
     *
     * <ul>
     *   <li><b>transfer-encoding</b>: Spring Bootが自動的にチャンク転送を設定</li>
     *   <li><b>connection</b>: HTTP/1.1コネクション管理（Keep-Alive等）</li>
     *   <li><b>keep-alive</b>: コネクション維持設定</li>
     *   <li><b>upgrade</b>: プロトコルアップグレード（WebSocket等）</li>
     *   <li><b>server</b>: サーバー情報の漏洩防止</li>
     *   <li><b>content-length</b>: Spring Bootが自動的に計算</li>
     * </ul>
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
     * リソースサーバーに転送する。認証済みの場合のみアクセストークンを付与する。
     *
     * <h3>処理フロー</h3>
     * <ol>
     *   <li>リクエストパスからHTTPメソッドとパスを取得</li>
     *   <li>UriBuilderで安全なURIを構築（クエリパラメータ自動エンコード）</li>
     *   <li>認証状態を確認し、認証済みの場合のみアクセストークンを設定</li>
     *   <li>Content-Typeヘッダーを設定</li>
     *   <li>リソースサーバーにリクエストを転送（30秒タイムアウト）</li>
     *   <li>レスポンスのステータスコード・ヘッダー・ボディを保持して返却</li>
     * </ol>
     *
     * <h3>認証・権限制御</h3>
     * <ul>
     *   <li>認証チェック: BFFでは行わない（リソースサーバーに委譲）</li>
     *   <li>権限チェック: リソースサーバー側で実施</li>
     *   <li>未認証の場合: トークンなしでリソースサーバーへ転送</li>
     *   <li>認証済みの場合: アクセストークンを付与してリソースサーバーへ転送</li>
     * </ul>
     *
     * @param request HTTPリクエスト
     * @param body リクエストボディ（GET/DELETEの場合はnull）
     * @return リソースサーバーからのレスポンス（ステータスコード・ヘッダー・ボディ）
     */
    @RequestMapping("/**")
    public ResponseEntity<String> proxyAll(
        HttpServletRequest request,
        @RequestBody(required = false) String body
    ) {
        // リクエスト情報を取得
        String method = request.getMethod();
        String path = request.getRequestURI().replace("/api", "");

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

        // ボディ設定 + ヘッダー設定
        WebClient.RequestHeadersSpec<?> headersSpec;

        if ("GET".equals(method) || "DELETE".equals(method)) {
            // GET/DELETE: ボディなし
            headersSpec = bodySpec;
        } else {
            // POST/PUT/PATCH: ボディ設定
            if (body != null && !body.isEmpty()) {
                headersSpec = bodySpec.bodyValue(body);
            } else {
                headersSpec = bodySpec;
            }
        }

        // 認証状態を確認
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        final boolean isAuthenticated = authentication != null
            && authentication.isAuthenticated()
            && !(authentication instanceof AnonymousAuthenticationToken);

        // ヘッダー設定（共通）
        headersSpec = headersSpec.headers(h -> {
            // 認証済みの場合のみアクセストークンを付与
            if (isAuthenticated && authentication != null) {
                OAuth2AuthorizedClient client = authorizedClientRepository.loadAuthorizedClient(
                    "idp",
                    authentication,
                    request
                );

                if (client != null && client.getAccessToken() != null) {
                    h.setBearerAuth(client.getAccessToken().getTokenValue());
                } else {
                    log.warn("Authenticated user {} has no access token", authentication.getName());
                }
            }

            // リクエストのContent-Typeをリソースサーバーに転送
            String contentType = request.getContentType();
            if (contentType != null) {
                h.setContentType(MediaType.parseMediaType(contentType));
            }
        });

        try {
            // リクエスト実行（ステータスコード・ヘッダー・ボディをすべて保持）
            ResponseEntity<String> response = headersSpec
                .exchangeToMono(clientResponse -> {
                    // ステータスコードを保持
                    HttpStatusCode statusCode = clientResponse.statusCode();

                    // レスポンスヘッダーをフィルタリング
                    HttpHeaders originalHeaders = clientResponse.headers().asHttpHeaders();
                    HttpHeaders filteredHeaders = new HttpHeaders();
                    originalHeaders.forEach((name, values) -> {
                        if (!EXCLUDED_RESPONSE_HEADERS.contains(name.toLowerCase())) {
                            filteredHeaders.addAll(name, values);
                        }
                    });

                    // ボディを取得してレスポンスを構築
                    // 空のレスポンス（204 No Content等）の場合はdefaultIfEmptyで空文字列を設定
                    return clientResponse.bodyToMono(String.class)
                        .defaultIfEmpty("") // 空の場合のデフォルト値
                        .map(
                            responseBody -> ResponseEntity
                                .status(statusCode)
                                .headers(filteredHeaders)
                                .body(responseBody)
                        );
                })
                .block();

            if (response == null) {
                log.error("WebClient returned null response - possible timeout or connection error");
                throw new RuntimeException("リソースサーバーへのリクエストが失敗しました");
            }

            return response;

        } catch (Exception e) {
            log.error("Error during API proxy request", e);
            throw e;
        }
    }
}
