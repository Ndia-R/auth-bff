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
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
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
    private final OAuth2AuthorizedClientService authorizedClientService;

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
        final String principalName = (authentication != null && isAuthenticated) ? authentication.getName() : null;

        // ヘッダー設定（共通）
        headersSpec = headersSpec.headers(h -> {
            // 認証済みの場合のみアクセストークンを付与
            if (isAuthenticated && principalName != null) {
                OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                    "idp",
                    principalName
                );

                if (client != null && client.getAccessToken() != null) {
                    h.setBearerAuth(client.getAccessToken().getTokenValue());
                    log.debug("Access token added for authenticated user: {}", principalName);
                } else {
                    log.warn("Authenticated user {} has no access token", principalName);
                }
            } else {
                log.debug("Anonymous request - no access token added");
            }

            // リクエストのContent-Typeをリソースサーバーに転送
            String contentType = request.getContentType();
            if (contentType != null) {
                h.setContentType(MediaType.parseMediaType(contentType));
            }
        });

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
                // 空のレスポンス（204 No Content等）の場合はdefaultIfEmptyで空文字列を設定
                return response.bodyToMono(String.class)
                    .defaultIfEmpty("") // 空の場合のデフォルト値
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
