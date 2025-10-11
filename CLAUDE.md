# Auth BFF (Backend for Frontend) プロジェクト

# 重要

基本的なやりとりは日本語でおこなってください。

## Claude Code作業ルール

**コード修正前の確認必須**
- ファイルの修正・変更を行う前に、必ずユーザーに修正内容を提示して許可を取る
- 勝手にコードを変更してはいけない
- 修正案を説明し、ユーザーの承認を得てから実行する

## 概要

KeycloakとのOAuth2認証フローを処理する**必要最小限のSpring Boot BFF (Backend for Frontend)** アプリケーションです。

### 🎯 特徴

1. **真のBFFパターン実装**: フロントエンドはトークンを一切扱わず、BFFがすべてのAPI呼び出しをプロキシ
   - XSS攻撃からトークンを完全に保護
   - セッションCookieのみでAPIアクセス可能

2. **PKCE対応**: Authorization Code with PKCEによるセキュアなOAuth2認証

3. **最小構成**: 14ファイルのみで構成された、保守しやすいシンプルな設計
   - 未使用のクラス・メソッドは一切なし
   - Spring Boot自動設定を最大限活用

4. **完全なCSRF保護**: CookieベースのCSRFトークンで状態変更操作を保護

5. **OpenID Connect準拠**: RP-Initiated Logoutによる確実なKeycloakセッション無効化

## アーキテクチャ

### 認証フロー（PKCE対応）
```
フロントエンド (SPA)
    ↓ Cookie: BFFSESSIONID + XSRF-TOKEN
   BFF (APIゲートウェイ)
    ├─ 認証管理 (/bff/auth/*)
    ├─ APIプロキシ (/api/books/*, /api/music/*)
    └─ トークン管理（Redisセッション）
    ↓ Authorization: Bearer <access_token>
リソースサーバー (API)
    ├─ 書籍API (/books/*)
    └─ 音楽API (/music/*)
```

### 主要コンポーネント（最小構成）
- **AuthController**: 認証エンドポイント（ログイン・ログアウト・ユーザー情報）
- **ApiProxyController**: APIプロキシ（書籍・音楽API転送、トークン自動付与）
- **AuthService**: 認証ビジネスロジック（ログアウト、ユーザー情報取得、認証状態確認）
- **TokenService**: トークン有効期限チェック（リフレッシュはSpring Securityが自動処理）
- **SecurityConfig**: Spring Security設定（PKCE、CSRF保護、CORS）
- **GlobalExceptionHandler**: 統一エラーハンドリング（認証エラー、Keycloak通信エラー）
- **RedisConfig**: Spring Session Data Redis設定（自動設定を使用）

## エンドポイント

### 認証エンドポイント

| メソッド | パス | 説明 | レスポンス |
|---------|------|------|-----------|
| GET | `/bff/auth/login` | 認証状態確認・OAuth2フロー開始 | リダイレクト |
| GET | `/bff/auth/user` | 現在のユーザー情報取得 | `UserResponse` |
| POST | `/bff/auth/logout` | ログアウト・セッションクリア | `LogoutResponse` |
| POST | `/bff/auth/logout?complete=true` | 完全ログアウト（Keycloakセッションも無効化） | `LogoutResponse` |
| GET | `/actuator/health` | ヘルスチェック | Spring Boot Actuator標準レスポンス |

### APIプロキシエンドポイント（新規追加）

| メソッド | パス | 説明 | 転送先 |
|---------|------|------|--------|
| GET/POST/PUT/DELETE | `/api/books/**` | 書籍APIプロキシ | `${RESOURCE_SERVER_URL}/books/**` |
| GET/POST/PUT/DELETE | `/api/music/**` | 音楽APIプロキシ | `${RESOURCE_SERVER_URL}/music/**` |

**重要**: フロントエンドは `/api/books/*` や `/api/music/*` を呼び出すだけで、BFFが自動的にアクセストークンを付与してリソースサーバーに転送します。フロントエンドはトークンを一切扱いません。

## DTOクラス

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


### ErrorResponse
統一的なエラーレスポンス形式。すべてのエラーで同じ構造を返却します。

#### 基本エラー例
```json
{
  "error": "UNAUTHORIZED",
  "message": "認証が必要です",
  "status": 401,
  "path": "/bff/auth/login",
  "timestamp": "2025-01-20 10:30:45"
}
```

#### WebClient/Keycloak通信特有のエラーコード
| エラーコード | HTTPステータス | 説明 |
|-------------|---------------|------|
| `KEYCLOAK_CLIENT_ERROR` | 400 | Keycloak通信でクライアントエラー |
| `KEYCLOAK_SERVER_ERROR` | 503 | Keycloak サーバーエラー |
| `KEYCLOAK_CONNECTION_ERROR` | 503 | Keycloak接続エラー |

## セキュリティ設定

### CSRF保護（新規追加）
- **有効化**: ✅ CookieCsrfTokenRepository使用
- **CSRFトークンCookie**: `XSRF-TOKEN` (HttpOnly=false)
- **CSRFトークンヘッダー**: `X-XSRF-TOKEN`
- **保護対象**: POST, PUT, DELETE, PATCH

**フロントエンド実装例:**
```javascript
// CSRFトークンをCookieから取得してヘッダーに設定
const csrfToken = document.cookie
  .split('; ')
  .find(row => row.startsWith('XSRF-TOKEN='))
  ?.split('=')[1];

fetch('/api/books', {
  method: 'POST',
  credentials: 'include',
  headers: {
    'Content-Type': 'application/json',
    'X-XSRF-TOKEN': csrfToken  // CSRF保護
  },
  body: JSON.stringify({ title: '新しい本' })
});
```

### セッション管理
- **ストレージ**: Redis
- **タイムアウト**: 30分
- **Cookie名**: BFFSESSIONID
- **属性**: HttpOnly, Secure, SameSite=lax

### CORS設定
- **許可オリジン**: 環境変数 `${CORS_ALLOWED_ORIGINS}` から読み込み
- **許可メソッド**: GET, POST, PUT, DELETE, OPTIONS
- **許可ヘッダー**: Authorization, Content-Type, X-XSRF-TOKEN
- **資格情報**: 許可

### OAuth2設定
- **プロバイダー**: Keycloak
- **フロー**: Authorization Code with PKCE
- **スコープ**: openid, profile, email

## 環境変数

### 基本設定
```bash
# ============================================
# Keycloak Configuration
# ============================================
KEYCLOAK_CLIENT_ID=my-books-client
KEYCLOAK_CLIENT_SECRET=your-client-secret
KEYCLOAK_REDIRECT_URI=http://localhost:8888/bff/login/oauth2/code/keycloak

# 本番環境（シンプルな設定）
# KEYCLOAK_ISSUER_URI=https://auth.example.com/realms/test-user-realm

# 開発環境（個別エンドポイント指定でネットワーク分離問題を解決）
KEYCLOAK_AUTHORIZE_URI=http://localhost:8180/realms/test-user-realm/protocol/openid-connect/auth
KEYCLOAK_TOKEN_URI=http://keycloak:8080/realms/test-user-realm/protocol/openid-connect/token
KEYCLOAK_JWK_URI=http://keycloak:8080/realms/test-user-realm/protocol/openid-connect/certs
KEYCLOAK_LOGOUT_URI=http://keycloak:8080/realms/test-user-realm/protocol/openid-connect/logout
KEYCLOAK_POST_LOGOUT_REDIRECT_URI=http://localhost:5173/logout-complete

# ============================================
# Redis Configuration
# ============================================
REDIS_HOST=redis
REDIS_PORT=6379

# ============================================
# Application Configuration (新規追加)
# ============================================
# フロントエンドURL
FRONTEND_URL=http://localhost:5173

# リソースサーバーURL
RESOURCE_SERVER_URL=http://api.example.com

# CORS許可オリジン（カンマ区切り）
CORS_ALLOWED_ORIGINS=http://localhost:5173,http://localhost:*
```

## 開発環境

### 環境概要
このプロジェクトは **WSL2上のUbuntu** で **VSCode DevContainer** + **Docker Compose** を使用して開発しています。Claude Code対応の完全な開発環境を提供します。

```
WSL2 (Ubuntu) → VSCode DevContainer → Docker Compose
                                          ├── auth-bff (開発コンテナ)
                                          ├── redis (セッションストレージ)
                                          └── keycloak (認証サーバー)
```

### DevContainer構成

#### 基本情報
- **コンテナ名**: `auth-bff`
- **ベースイメージ**: `eclipse-temurin:17-jdk-jammy`
- **実行ユーザー**: `vscode`
- **作業ディレクトリ**: `/workspace`
- **コマンド**: `sleep infinity` (DevContainer用)

#### インストール済みツール
| ツール | バージョン | 用途 |
|--------|-----------|------|
| Java (Eclipse Temurin) | 17 | アプリケーション実行環境 |
| Gradle | ラッパー経由 | ビルドツール |
| Python | 3.10.12 | Serena MCP用 |
| uv | 最新 | Pythonパッケージマネージャー (Serena MCP) |
| Git | 最新 | バージョン管理 |

#### VSCode拡張機能
`.devcontainer/devcontainer.json`で以下の拡張機能が自動インストールされます：
- `vscjava.vscode-java-pack` - Java開発パック
- `mhutchie.git-graph` - Git履歴可視化
- `streetsidesoftware.code-spell-checker` - スペルチェック
- `shengchen.vscode-checkstyle` - Checkstyle連携
- `cweijan.vscode-database-client2` - データベースクライアント
- `anthropic.claude-code` - Claude Code AI開発支援

#### 永続化ボリューム
DevContainer再起動後もデータを保持するために以下のボリュームをマウント：

| ボリューム名 | マウント先 | 用途 |
|------------|-----------|------|
| (プロジェクトディレクトリ) | `/workspace` | ソースコード |
| `gradle-cache` | `/home/vscode/.gradle` | Gradleキャッシュ |
| `claude-config` | `/home/vscode/.claude` | Claude Code設定・認証情報 |

#### 環境変数
DevContainerは`.env`ファイルまたはdocker-compose.ymlから以下の環境変数を読み込みます：
```bash
# Keycloak設定
KEYCLOAK_CLIENT_ID=my-books-client
KEYCLOAK_CLIENT_SECRET=your-client-secret
KEYCLOAK_REDIRECT_URI=http://localhost:8888/bff/login/oauth2/code/keycloak
KEYCLOAK_AUTHORIZE_URI=http://localhost:8180/realms/test-user-realm/protocol/openid-connect/auth
KEYCLOAK_TOKEN_URI=http://keycloak:8080/realms/test-user-realm/protocol/openid-connect/token
KEYCLOAK_JWK_URI=http://keycloak:8080/realms/test-user-realm/protocol/openid-connect/certs

# Redis設定
REDIS_HOST=redis
REDIS_PORT=6379
```

### Docker Compose サービス構成

#### 1. auth-bff (開発コンテナ)
```yaml
ports: 8888:8080  # 外部:内部
networks: shared-network
depends_on: [redis, keycloak]
```
- Spring Bootアプリケーションを実行
- Claude Code開発環境
- コンテナ間通信: `http://keycloak:8080`, `redis:6379`
- 外部アクセス: `http://localhost:8888`

#### 2. redis (セッションストレージ)
```yaml
image: redis:8.2
ports: 6379:6379
networks: shared-network
```
- BFFのセッションデータを保存
- Spring Session Data Redisで使用

#### 3. keycloak (認証サーバー)
```yaml
image: quay.io/keycloak/keycloak:26.3.3
ports: 8180:8080
networks: shared-network
command: start-dev --import-realm
```
- OAuth2/OpenID Connect認証サーバー
- `realm-export.json`から自動設定インポート
- 管理コンソール: `http://localhost:8180` (admin/admin)
- 外部アクセス: `http://localhost:8180`
- コンテナ内部: `http://keycloak:8080`

### ネットワーク構成
```
外部ブラウザ
    ↓ http://localhost:8888
auth-bff:8080 ←→ redis:6379
    ↓ http://keycloak:8080 (内部通信)
keycloak:8080
    ↑ http://localhost:8180 (外部アクセス)
外部ブラウザ
```

**重要**: OAuth2認証フローでは、ブラウザは`localhost:8180`、BFFは`keycloak:8080`を使用します。

### 開発環境起動

#### 初回起動
```bash
# 1. Docker Compose環境起動
docker compose up -d

# 2. VSCodeでDevContainer接続
code .
# VSCodeコマンドパレット: "Dev Containers: Reopen in Container"

# 3. 起動確認
./gradlew --version
```

#### 日常的な起動
```bash
# VSCodeでプロジェクトを開く
code .
# 自動的にDevContainerが起動します
```

#### コンテナ内で作業
```bash
# auth-bffコンテナに直接接続
docker compose exec auth-bff bash

# アプリケーション実行
./gradlew bootRun
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

## プロジェクト構成

### 📁 ソースコード構成（最小構成・14ファイル）

```
src/main/java/com/example/auth_bff/
├── AuthBffApplication.java              # メインクラス
│
├── config/                              # 設定（4ファイル）
│   ├── CsrfCookieFilter.java           # CSRF Cookie自動設定フィルター
│   ├── RedisConfig.java                 # Redis/Spring Session設定
│   ├── SecurityConfig.java              # Spring Security + PKCE + CORS
│   └── WebClientConfig.java             # WebClient設定（Keycloak通信用）
│
├── controller/                          # コントローラー（2ファイル）
│   ├── ApiProxyController.java          # APIプロキシ（/api/books/**, /api/music/**）
│   └── AuthController.java              # 認証エンドポイント（/bff/auth/*）
│
├── dto/                                 # DTO（3ファイル）
│   ├── ErrorResponse.java               # 統一エラーレスポンス
│   ├── LogoutResponse.java              # ログアウトレスポンス
│   └── UserResponse.java                # ユーザー情報レスポンス
│
├── exception/                           # 例外（2ファイル）
│   ├── GlobalExceptionHandler.java      # 統一エラーハンドラー
│   └── UnauthorizedException.java       # 認証エラー例外（唯一のカスタム例外）
│
└── service/                             # サービス（2ファイル）
    ├── AuthService.java                 # 認証ビジネスロジック
    └── TokenService.java                # トークン有効期限チェック
```

### 🎯 設計原則

1. **必要最小限の構成**: すべてのクラスとメソッドが実際に使用されている
2. **真のBFFパターン**: フロントエンドはトークンを一切扱わない
3. **Spring Boot自動設定の活用**: カスタムBean最小限
4. **シンプルなエラーハンドリング**: 実際に発生する例外のみ処理

### 📊 削除された未使用要素（2025-01-20）

以下の要素は使用されていないため削除され、よりシンプルな構成になりました：

- ❌ `AccessTokenResponse.java` - トークン公開用DTO（BFFパターンでは不要）
- ❌ `BadRequestException.java` - 未使用例外
- ❌ `ValidationException.java` - 未使用例外
- ❌ `ConflictException.java` - 未使用例外
- ❌ `NotFoundException.java` - 未使用例外
- ❌ `ForbiddenException.java` - 未使用例外
- ❌ `AuthService.getAccessToken()` - 未使用メソッド
- ❌ `AuthService.refreshAccessToken()` - 重複メソッド
- ❌ `RedisConfig.redisTemplate` Bean - Spring Session自動設定を使用

## 開発時の注意点

### コーディングスタイル
- **早期例外**: null チェック後即座に例外をスロー
- **型安全**: 具体的なDTOクラスを使用
- **単一責任**: Controller/Service/Repository の明確な分離
- **必要最小限**: 未使用のクラス・メソッドは作らない

### エラーハンドリング
- 認証エラー: `UnauthorizedException`（唯一のカスタム例外）
- Keycloak通信エラー: `WebClientException`, `WebClientResponseException`
- その他一般エラー: `Exception`
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

## API使用例（BFFパターン）

### 🎯 重要: フロントエンドはトークンを一切扱いません

真のBFFパターンでは、フロントエンドは**セッションCookieのみ**を使用し、トークンは完全にBFF側で管理されます。

### 認証フロー

```javascript
// 1. ログイン開始（未認証の場合Keycloakにリダイレクト）
window.location.href = '/bff/auth/login';

// 2. 認証後、フロントエンドにリダイレクト（例: /auth-callback）
// セッションCookie (BFFSESSIONID) と CSRFトークン (XSRF-TOKEN) が自動的に設定される

// 3. ユーザー情報取得
fetch('/bff/auth/user', {
  credentials: 'include'  // セッションCookieを送信
})
  .then(response => response.json())
  .then(user => {
    console.log(user.name);  // "田中太郎"
    console.log(user.email); // "tanaka@example.com"
  });

// 4. ログアウト
// 通常ログアウト（BFFセッションのみクリア）
fetch('/bff/auth/logout', {
  method: 'POST',
  credentials: 'include'
})
  .then(response => response.json())
  .then(data => {
    console.log(data.message); // "success"
  });

// 完全ログアウト（BFFセッション + Keycloakセッションクリア）
fetch('/bff/auth/logout?complete=true', {
  method: 'POST',
  credentials: 'include'
})
  .then(response => response.json())
  .then(data => {
    console.log(data.message); // "success"
    window.location.href = '/';  // トップページにリダイレクト
  });
```

### APIプロキシの使用（書籍・音楽API）

```javascript
// CSRFトークンを取得するヘルパー関数
function getCsrfToken() {
  return document.cookie
    .split('; ')
    .find(row => row.startsWith('XSRF-TOKEN='))
    ?.split('=')[1];
}

// GET リクエスト（書籍一覧取得）
fetch('/api/books/list', {
  credentials: 'include'  // セッションCookieのみ
})
  .then(response => response.json())
  .then(books => {
    console.log(books);
  });

// POST リクエスト（新しい書籍を追加）
fetch('/api/books', {
  method: 'POST',
  credentials: 'include',
  headers: {
    'Content-Type': 'application/json',
    'X-XSRF-TOKEN': getCsrfToken()  // CSRF保護
  },
  body: JSON.stringify({
    title: 'Spring Security実践ガイド',
    author: '山田太郎'
  })
})
  .then(response => response.json())
  .then(book => {
    console.log('作成された書籍:', book);
  });

// 音楽APIも同様
fetch('/api/music/playlist', {
  credentials: 'include'
})
  .then(response => response.json())
  .then(playlist => {
    console.log(playlist);
  });
```

### ✅ このパターンの利点

1. **トークン完全隠蔽**: フロントエンドはトークンを見ることも触ることもできない
2. **XSS攻撃対策**: JavaScriptからトークンにアクセス不可
3. **シンプルな実装**: セッションCookieだけを意識すればよい
4. **自動リフレッシュ**: BFFが裏でトークンを自動更新

---

🚀 **Ready for Production!** このBFFは本番環境でのOAuth2認証フローに対応しています。