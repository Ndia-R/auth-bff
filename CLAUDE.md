# Auth BFF (Backend for Frontend) プロジェクト

# 重要

基本的なやりとりは日本語でおこなってください。

## Claude Code作業ルール

**コード修正前の確認必須**
- ファイルの修正・変更を行う前に、必ずユーザーに修正内容を提示して許可を取る
- 勝手にコードを変更してはいけない
- 修正案を説明し、ユーザーの承認を得てから実行する

## 概要
KeycloakとのOAuth2認証フローを処理するSpring BootのBFF (Backend for Frontend)アプリケーションです。PKCE（Proof Key for Code Exchange）対応により、よりセキュアなOAuth2認証を実現しています。

## アーキテクチャ

### 認証フロー（PKCE対応）
```
フロント(SPA) → BFF → Keycloak → BFF → フロント
     ↓               ↓        ↓       ↓
  /bff/auth/login  OAuth2   JWT    SessionCookie
                  (PKCE)
```

### 主要コンポーネント
- **AuthController**: HTTP認証エンドポイント
- **AuthService**: 認証ビジネスロジック
- **TokenService**: OAuth2トークン管理
- **SecurityConfig**: Spring Security設定（PKCE対応）
- **GlobalExceptionHandler**: 統一エラーハンドリング

## エンドポイント

| メソッド | パス | 説明 | レスポンス |
|---------|------|------|-----------|
| GET | `/bff/auth/login` | 認証状態確認・OAuth2フロー開始 | リダイレクト |
| GET | `/bff/auth/token` | アクセストークン取得 | `AccessTokenResponse` |
| POST | `/bff/auth/refresh` | アクセストークンリフレッシュ | `AccessTokenResponse` |
| GET | `/bff/auth/user` | 現在のユーザー情報取得 | `UserResponse` |
| POST | `/bff/auth/logout` | ログアウト・セッションクリア | `LogoutResponse` |
| POST | `/bff/auth/logout?complete=true` | 完全ログアウト（Keycloakセッションも無効化） | `LogoutResponse` |
| GET | `/bff/auth/health` | ヘルスチェック | `HealthResponse` |

## DTOクラス

### AccessTokenResponse
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expires_in": 3600,
  "token_type": "Bearer"
}
```

### UserResponse
```json
{
  "name": "田中太郎",
  "email": "tanaka@example.com",
  "preferred_username": "tanaka"
}
```

### LogoutResponse
通常ログアウトと完全ログアウトの両方で同じレスポンス形式を返します。
```json
{
  "message": "success"
}
```

**ログアウトの種類:**
- **通常ログアウト** (`complete=false` または省略): BFFセッションのみクリア
- **完全ログアウト** (`complete=true`): BFFセッション + Keycloakセッションをクリア

### HealthResponse
```json
{
  "status": "UP",
  "service": "auth-bff"
}
```

### ErrorResponse
```json
{
  "error": "UNAUTHORIZED",
  "message": "認証が必要です",
  "status": 401,
  "path": "/bff/auth/login",
  "timestamp": "2025-01-20 10:30:45"
}
```

## セキュリティ設定

### セッション管理
- **ストレージ**: Redis
- **タイムアウト**: 30分
- **Cookie名**: BFFSESSIONID
- **属性**: HttpOnly, Secure, SameSite=lax

### CORS設定
- **許可オリジン**: `http://app.example.com*`, `http://localhost:*`
- **許可メソッド**: GET, POST, PUT, DELETE, OPTIONS
- **資格情報**: 許可

### OAuth2設定
- **プロバイダー**: Keycloak
- **フロー**: Authorization Code
- **スコープ**: openid, profile, email

## 環境変数

### 基本設定
```bash
# Keycloak設定
KEYCLOAK_CLIENT_ID=my-books-client
KEYCLOAK_CLIENT_SECRET=your-client-secret
KEYCLOAK_REDIRECT_URI=http://localhost:8888/bff/login/oauth2/code/keycloak

# 本番環境（シンプルな設定）
KEYCLOAK_ISSUER_URI=https://auth.example.com/realms/test-user-realm

# 開発環境（個別エンドポイント指定でネットワーク分離問題を解決）
KEYCLOAK_AUTHORIZE_URI=http://localhost:8180/realms/test-user-realm/protocol/openid-connect/auth
KEYCLOAK_TOKEN_URI=http://keycloak:8080/realms/test-user-realm/protocol/openid-connect/token
KEYCLOAK_JWK_URI=http://keycloak:8080/realms/test-user-realm/protocol/openid-connect/certs

# Redis設定
REDIS_HOST=redis
REDIS_PORT=6379

# セキュリティ設定
COOKIE_SECURE=false
COOKIE_SAME_SITE=lax
SESSION_TIMEOUT=30m
```

## 開発環境

### Docker Compose環境
このプロジェクトは **WSL2上のUbuntu** で **Docker Compose** を使用して開発しています。Claude Code対応の完全な開発環境を提供します。

```
WSL2 (Ubuntu) → Docker Compose
                   ├── auth-bff (Eclipse Temurin 17 + Claude Code)
                   ├── redis (セッションストレージ)
                   └── keycloak (認証サーバー + realm-export.json)
```

### 開発環境の構成
- **プラットフォーム**: WSL2 + Ubuntu + Docker Compose
- **IDE**: Claude Code (Anthropic AI) + VSCode対応
- **Java**: Eclipse Temurin 17
- **コンテナ構成**:
  - `auth-bff`: アプリケーションコンテナ (port 8888:8080)
  - `redis`: セッションストレージ (port 6379)
  - `keycloak`: 認証サーバー (port 8180:8080)

### 開発環境起動
```bash
# Docker Compose環境起動
docker compose up -d

# auth-bffコンテナに接続してClaude Code使用
docker compose exec auth-bff bash

# または、VSCodeでDevContainer接続
code .
# Dev Containers: Reopen in Container
```

## ビルド・実行

```bash
# ビルド
./gradlew build

# テスト実行
./gradlew test

# アプリケーション実行
./gradlew bootRun

# Docker Compose実行（完全環境）
docker compose up -d
```

## 開発時の注意点

### コーディングスタイル
- **早期例外**: null チェック後即座に例外をスロー
- **型安全**: 具体的なDTOクラスを使用
- **単一責任**: Controller/Service/Repository の明確な分離

### エラーハンドリング
- 認証エラー: `UnauthorizedException`
- バリデーションエラー: `ValidationException`
- その他ビジネス例外: 適切なカスタム例外を使用
- すべて`GlobalExceptionHandler`で統一処理

### テスト
```bash
# 単体テスト
./gradlew test

# 結合テスト（要Redis起動）
docker compose up redis -d
./gradlew test
```

## トラブルシューティング

### よくある問題

1. **CORS エラー**
   - `SecurityConfig.corsConfigurationSource()` で許可オリジンを確認

2. **認証ループ**
   - Keycloak設定のリダイレクトURI確認
   - `application.yml` の OAuth2設定確認

3. **セッション問題**
   - Redis接続確認
   - Cookie設定（Secure属性）確認

4. **🔥 完全ログアウト後も再認証が発生しない**

   **症状**: 完全ログアウト(`complete=true`)後の再ログインでKeycloak認証画面が表示されない

   **原因**: Keycloakセッションが実際には無効化されていない
   ```java
   // 問題のある実装
   private void processKeycloakLogout() {
       // 単純にログアウト画面のHTMLを取得するだけ
       webClient.get().uri(keycloakLogoutUri).retrieve()...
   }
   ```

   **解決方法**: OpenID Connect RP-Initiated Logoutを使用
   ```java
   // 正しい実装
   private void processKeycloakLogout(OAuth2User principal) {
       if (principal instanceof OidcUser) {
           String idToken = ((OidcUser) principal).getIdToken().getTokenValue();
           String endSessionUrl = UriComponentsBuilder
               .fromUriString(keycloakLogoutUri)
               .queryParam("id_token_hint", idToken)  // 重要: ユーザー識別
               .queryParam("post_logout_redirect_uri", postLogoutRedirectUri)
               .build().toUriString();
           webClient.get().uri(endSessionUrl).retrieve()...
       }
   }
   ```

   **確認方法**:
   ```bash
   # ログでエンドセッションURLを確認
   tail -f logs/application.log | grep "end session endpoint"

   # 完全ログアウト後にKeycloakに直接アクセスして確認
   curl -v http://localhost:8180/realms/test-user-realm/account
   ```

5. **🔥 DevContainer環境でのKeycloak接続エラー**

   **症状**: `Unable to resolve Configuration with the provided Issuer of "http://localhost:8180/realms/test-user-realm"`

   **原因**: WSL2 + DevContainer環境でのネットワーク構成の違い
   ```
   WSL2 (Ubuntu) + VSCode DevContainer環境
   ├── auth-bff (DevContainer内) :8080 → :8888 (外部)
   ├── keycloak (Docker) :8080 → :8180 (外部)
   └── redis (Docker) :6379 → :6379 (外部)
   ```

   **解決方法**: OAuth2認証フローにおけるURL設定の使い分け

   - **issuer-uri**: Spring Bootアプリ（コンテナ内）がKeycloakのメタデータを取得するため
     ```bash
     KEYCLOAK_ISSUER_URI=http://keycloak:8080/realms/test-user-realm  # ← コンテナ間通信
     ```

   - **redirect-uri**: ブラウザがKeycloakから戻ってくる際のコールバックURL
     ```bash
     KEYCLOAK_REDIRECT_URI=http://localhost:8888/bff/login/oauth2/code/keycloak  # ← 外部アクセス
     ```

   **正しい起動コマンド**:
   ```bash
   # 開発環境用（すべての環境変数を明示）
   KEYCLOAK_CLIENT_ID=my-books-client \
   KEYCLOAK_CLIENT_SECRET=your-client-secret \
   KEYCLOAK_REDIRECT_URI=http://localhost:8888/bff/login/oauth2/code/keycloak \
   KEYCLOAK_AUTHORIZE_URI=http://localhost:8180/realms/test-user-realm/protocol/openid-connect/auth \
   KEYCLOAK_TOKEN_URI=http://keycloak:8080/realms/test-user-realm/protocol/openid-connect/token \
   KEYCLOAK_JWK_URI=http://keycloak:8080/realms/test-user-realm/protocol/openid-connect/certs \
   REDIS_HOST=redis \
   REDIS_PORT=6379 \
   ./gradlew bootRun

   # .envファイルを使用する場合
   # （docker-compose.ymlが.envを自動読み込み）
   ./gradlew bootRun
   ```

6. **🔥 OAuth2ネットワーク分離問題（重要）**

   **症状**: ブラウザで「このサイトにアクセスできません」、ログに`http://keycloak:8080`へのリダイレクト

   **根本原因**: OAuth2認証フローでのネットワーク分離
   ```
   OAuth2認証フロー:
   1. BFF → Keycloak (メタデータ取得)     : コンテナ内部通信
   2. ブラウザ → Keycloak (認証画面)     : ホストネットワーク  ⚠️ここで失敗
   3. Keycloak → BFF (コールバック)      : ホストネットワーク
   4. BFF → Keycloak (トークン交換)      : コンテナ内部通信
   ```

   **問題の詳細**:
   - Spring SecurityがKeycloakのメタデータから`authorization_endpoint`を自動取得
   - メタデータはコンテナ内部URL(`keycloak:8080`)を含む
   - ブラウザは`keycloak:8080`を解決できない

   **完全な解決方法**: `application.yml`で明示的なエンドポイント指定

   ```yaml
   spring:
     security:
       oauth2:
         client:
           provider:
             keycloak:
               # 本番環境では以下のissuer-uriのみ使用
               # issuer-uri: ${KEYCLOAK_ISSUER_URI}

               # 開発環境では個別エンドポイント指定
               authorization-uri: ${KEYCLOAK_AUTHORIZE_URI}  # ブラウザ用（外部）
               token-uri: ${KEYCLOAK_TOKEN_URI}             # トークン交換用（内部）
               jwk-set-uri: ${KEYCLOAK_JWK_URI}             # JWT検証用（内部）
   ```

   **環境変数設定** (`.env`ファイル):
   ```bash
   # 基本設定
   KEYCLOAK_CLIENT_ID=my-books-client
   KEYCLOAK_CLIENT_SECRET=your-client-secret
   KEYCLOAK_REDIRECT_URI=http://localhost:8888/bff/login/oauth2/code/keycloak

   # 本番環境用（シンプル）
   KEYCLOAK_ISSUER_URI=https://auth.example.com/realms/test-user-realm

   # 開発環境用（ネットワーク分離対応）
   KEYCLOAK_AUTHORIZE_URI=http://localhost:8180/realms/test-user-realm/protocol/openid-connect/auth
   KEYCLOAK_TOKEN_URI=http://keycloak:8080/realms/test-user-realm/protocol/openid-connect/token
   KEYCLOAK_JWK_URI=http://keycloak:8080/realms/test-user-realm/protocol/openid-connect/certs
   ```

   **Spring Security内部の動作**:
   ```java
   // SecurityConfig.java の "keycloak" がapplication.ymlのregistration名と対応
   .oauth2Login(oauth2 -> oauth2.loginPage("/oauth2/authorization/keycloak"))

   // OAuth2AuthorizationRequestRedirectFilter が以下を実行:
   ClientRegistration keycloak = clientRegistrationRepository.findByRegistrationId("keycloak");
   String authUrl = keycloak.getProviderDetails().getAuthorizationUri();  // localhost:8180を使用
   // ブラウザを authUrl にリダイレクト
   ```

   **デバッグ方法**:
   ```bash
   # 起動時にClientRegistration情報をログ出力
   logging.level.org.springframework.security.oauth2: DEBUG

   # 期待する動作確認
   curl -v http://localhost:8888/bff/auth/login
   # → Location: http://localhost:8180/realms/test-user-realm/protocol/openid-connect/auth?...
   ```

   **この問題は毎回発生するため、必ずエンドポイントを明示的に設定すること！**

### 🔥 新機能・改善点

6. **ログアウト機能の強化**

   **機能**: 通常ログアウトと完全ログアウトの2段階対応
   - **通常ログアウト** (`complete=false`): BFFアプリケーションのセッションのみクリア
     - ユーザーがBFFアプリから離れたい場合
     - 他のKeycloakアプリケーションは継続利用可能
   - **完全ログアウト** (`complete=true`): BFFセッション + Keycloakセッションを完全クリア
     - セキュリティ要件が高い場合（共用端末等）
     - 全てのKeycloakアプリケーションからログアウト

   ```java
   // AuthService.java - ログアウト処理
   public LogoutResponse logout(HttpServletRequest request, HttpServletResponse response,
                               OAuth2User principal, boolean complete) {
       // BFFセッション関連のクリア（常に実行）
       invalidateSession(request, username);
       clearSecurityContext();
       clearSessionCookie(response);

       // Keycloakログアウト処理（完全ログアウト時のみ）
       if (complete) {
           processKeycloakLogout(principal);  // ← OAuth2Userを渡すように修正
       }

       return new LogoutResponse("success");
   }
   ```

   **🔧 Keycloakログアウト処理の改善** (OpenID Connect RP-Initiated Logout対応)
   ```java
   // 修正前: 問題のある実装
   private void processKeycloakLogout() {
       // 単純にログアウト画面のHTMLを取得するだけ（セッション無効化されない）
       webClient.get().uri(keycloakLogoutUri).retrieve()...
   }

   // 修正後: 正しい実装
   private void processKeycloakLogout(OAuth2User principal) {
       if (principal instanceof OidcUser) {
           OidcUser oidcUser = (OidcUser) principal;
           String idToken = oidcUser.getIdToken().getTokenValue();

           // OpenID Connect End Session Endpointを使用
           String endSessionUrl = UriComponentsBuilder
               .fromUriString(keycloakLogoutUri)
               .queryParam("id_token_hint", idToken)           // ユーザー識別・自動ログアウト
               .queryParam("post_logout_redirect_uri", postLogoutRedirectUri)
               .build()
               .toUriString();

           webClient.get().uri(endSessionUrl).retrieve()...   // 確実なセッション無効化
       }
   }
   ```

   **改善効果:**
   - ✅ **確実なKeycloakセッション無効化**: ID Tokenによるユーザー識別
   - ✅ **自動ログアウト**: 確認画面をスキップして直接ログアウト実行
   - ✅ **OpenID Connect仕様準拠**: 標準的なRP-Initiated Logout実装
   - ✅ **再認証の保証**: 完全ログアウト後は必ずKeycloak認証画面が表示

7. **PKCE (Proof Key for Code Exchange) 対応**

   **機能**: より安全なOAuth2認証フロー
   ```java
   // SecurityConfig.java
   @Bean
   public OAuth2AuthorizationRequestResolver pkceResolver(ClientRegistrationRepository clientRegistrationRepository) {
       DefaultOAuth2AuthorizationRequestResolver authorizationRequestResolver =
           new DefaultOAuth2AuthorizationRequestResolver(clientRegistrationRepository, "/oauth2/authorization");
       authorizationRequestResolver.setAuthorizationRequestCustomizer(
           OAuth2AuthorizationRequestCustomizers.withPkce()
       );
       return authorizationRequestResolver;
   }
   ```

7. **Claude Code開発環境の充実**

   **機能**: AI支援開発環境の完全対応
   - Dockerfileに`@anthropic-ai/claude-code`インストール
   - vscodeユーザーでの適切な権限管理
   - npm global、bash履歴、設定の永続化
   - Serena MCP対応（`uv`インストール済み）

8. **詳細ログ設定**

   **機能**: 開発時のデバッグを支援
   ```yaml
   logging:
     level:
       org.springframework.security.oauth2: DEBUG
       org.springframework.http.client: DEBUG
       org.apache.http: DEBUG
       org.springframework.web.client.RestTemplate: TRACE
   ```

9. **OpenID Connect RP-Initiated Logout対応**

   **機能**: 標準準拠のKeycloakプログラマティックログアウト
   - **従来の問題**: ログアウト画面のHTMLを取得するだけでセッション無効化されない
   - **改善後**: ID Token Hintを使用した確実なセッション無効化
   - **OpenID Connect仕様準拠**: [RFC準拠](https://openid.net/specs/openid-connect-rpinitiated-1_0.html)の実装
   - **管理用API不要**: 認証済みユーザーの情報のみでログアウト可能

   ```java
   // 実装のポイント
   @Value("${keycloak.post-logout-redirect-uri}")
   private String postLogoutRedirectUri;

   private void processKeycloakLogout(OAuth2User principal) {
       if (principal instanceof OidcUser) {
           String idToken = ((OidcUser) principal).getIdToken().getTokenValue();
           String endSessionUrl = UriComponentsBuilder
               .fromUriString(keycloakLogoutUri)
               .queryParam("id_token_hint", idToken)
               .queryParam("post_logout_redirect_uri", postLogoutRedirectUri)
               .build().toUriString();
           // 確実なセッション無効化を実行
       }
   }
   ```

10. **Keycloak設定の自動化**

   **機能**: `realm-export.json`による設定管理
   - クライアント設定、ユーザー、ロール等を含む
   - `docker compose up`で自動インポート
   - 開発環境の即座利用可能

### ログ確認
```bash
# 認証関連ログ
tail -f logs/application.log | grep -E "(OAuth2|Security|Auth)"

# エラーログ
tail -f logs/application.log | grep ERROR
```

## API使用例

### 認証フロー
```javascript
// 1. ログイン開始（未認証の場合Keycloakにリダイレクト）
// /bff/auth/loginは認証状態をチェックし、適切にリダイレクトします
window.location.href = '/bff/auth/login';

// 2. アクセストークン取得（認証後）
fetch('/bff/auth/token')
  .then(response => response.json())
  .then(data => {
    // アクセストークンを取得
    const accessToken = data.access_token;

    // APIサーバーへのリクエストで使用
    fetch('https://api.example.com/data', {
      headers: {
        'Authorization': `Bearer ${accessToken}`
      }
    });
  });

// 3. トークンリフレッシュ
fetch('/bff/auth/refresh', { method: 'POST' })
  .then(response => response.json())
  .then(data => {
    const newToken = data.access_token;
  });

// 4. ログアウト
// 通常ログアウト（BFFセッションのみクリア）
fetch('/bff/auth/logout', { method: 'POST' })
  .then(response => response.json())
  .then(data => {
    console.log(data.message); // "success"
  });

// 完全ログアウト（BFFセッション + Keycloakセッションクリア）
fetch('/bff/auth/logout?complete=true', { method: 'POST' })
  .then(response => response.json())
  .then(data => {
    console.log(data.message); // "success"
  });
```

---

🚀 **Ready for Production!** このBFFは本番環境でのOAuth2認証フローに対応しています。