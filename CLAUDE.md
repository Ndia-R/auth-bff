# Auth BFF (Backend for Frontend) プロジェクト

# 重要

基本的なやりとりは日本語でおこなってください。

## 概要
KeycloakとのOAuth2認証フローを処理するSpring BootのBFF (Backend for Frontend)アプリケーションです。

## アーキテクチャ

### 認証フロー
```
フロント(SPA) → BFF → Keycloak → BFF → フロント
     ↓               ↓        ↓       ↓
  /bff/auth/login  OAuth2   JWT    SessionCookie
```

### 主要コンポーネント
- **AuthController**: HTTP認証エンドポイント
- **AuthService**: 認証ビジネスロジック
- **TokenService**: OAuth2トークン管理
- **SecurityConfig**: Spring Security設定
- **GlobalExceptionHandler**: 統一エラーハンドリング

## エンドポイント

| メソッド | パス | 説明 | レスポンス |
|---------|------|------|-----------|
| GET | `/bff/auth/login` | 認証状態確認・トークン取得 | `AccessTokenResponse` |
| POST | `/bff/auth/refresh` | アクセストークンリフレッシュ | `AccessTokenResponse` |
| GET | `/bff/auth/user` | 現在のユーザー情報取得 | `UserResponse` |
| POST | `/bff/auth/logout` | ログアウト・セッションクリア | `{"message": "success"}` |
| GET | `/bff/auth/health` | ヘルスチェック | `{"status": "UP"}` |

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

```bash
# Keycloak設定
KEYCLOAK_CLIENT_ID=auth-bff-client
KEYCLOAK_CLIENT_SECRET=your-client-secret-here
KEYCLOAK_ISSUER_URI=http://auth.example.com/realms/your-realm
KEYCLOAK_REDIRECT_URI=http://app.example.com/bff/login/oauth2/code/keycloak

# Redis設定
REDIS_HOST=localhost
REDIS_PORT=6379
```

## 開発環境

### DevContainer環境
このプロジェクトは **WSL2上のUbuntu** で **VSCode DevContainer** を使用して開発しています。

```
WSL2 (Ubuntu) → Docker → DevContainer (auth-bff)
                   ↓
               Redis Container
```

### 開発環境の構成
- **プラットフォーム**: WSL2 + Ubuntu
- **IDE**: VSCode with DevContainer
- **Java**: Eclipse Temurin 17
- **コンテナ構成**:
  - `auth-bff`: アプリケーションコンテナ (port 8888:8080)
  - `redis`: セッションストレージ (port 6379)
- **外部依存**: Keycloak (別途起動が必要)

### DevContainer起動
```bash
# VSCodeでプロジェクトを開く
code .

# DevContainerで再開する（VSCode Command Palette）
> Dev Containers: Reopen in Container
```

## ビルド・実行

```bash
# ビルド
./gradlew build

# テスト実行
./gradlew test

# アプリケーション実行
./gradlew bootRun

# Docker Compose実行（DevContainer環境では不要）
docker compose up
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

4. **🔥 DevContainer環境でのKeycloak接続エラー**

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
   KEYCLOAK_ISSUER_URI=http://keycloak:8080/realms/test-user-realm \
   KEYCLOAK_REDIRECT_URI=http://localhost:8888/bff/login/oauth2/code/keycloak \
   REDIS_HOST=redis \
   REDIS_PORT=6379 \
   ./gradlew bootRun
   ```

5. **🔥 OAuth2ネットワーク分離問題（重要）**

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
               issuer-uri: ${KEYCLOAK_ISSUER_URI}  # メタデータ取得用（内部）
               authorization-uri: ${KEYCLOAK_AUTHORIZATION_URI}  # ブラウザ用（外部）
               token-uri: ${KEYCLOAK_TOKEN_URI}  # トークン交換用（内部）
               user-info-uri: ${KEYCLOAK_USERINFO_URI}  # ユーザー情報用（内部）
               jwk-set-uri: ${KEYCLOAK_JWK_SET_URI}  # JWT検証用（内部）
   ```

   **環境変数設定** (`.env`ファイル):
   ```bash
   # 基本設定
   KEYCLOAK_ISSUER_URI=http://keycloak:8080/realms/test-user-realm
   KEYCLOAK_REDIRECT_URI=http://localhost:8888/bff/login/oauth2/code/keycloak

   # ネットワーク分離対応
   KEYCLOAK_AUTHORIZATION_URI=http://localhost:8180/realms/test-user-realm/protocol/openid-connect/auth
   KEYCLOAK_TOKEN_URI=http://keycloak:8080/realms/test-user-realm/protocol/openid-connect/token
   KEYCLOAK_USERINFO_URI=http://keycloak:8080/realms/test-user-realm/protocol/openid-connect/userinfo
   KEYCLOAK_JWK_SET_URI=http://keycloak:8080/realms/test-user-realm/protocol/openid-connect/certs
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
fetch('/bff/auth/login')
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

// 2. トークンリフレッシュ
fetch('/bff/auth/refresh', { method: 'POST' })
  .then(response => response.json())
  .then(data => {
    const newToken = data.access_token;
  });

// 3. ログアウト
fetch('/bff/auth/logout', { method: 'POST' });
```

---

🚀 **Ready for Production!** このBFFは本番環境でのOAuth2認証フローに対応しています。