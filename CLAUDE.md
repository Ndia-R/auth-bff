# Auth BFF (Backend for Frontend) プロジェクト

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

## ビルド・実行

```bash
# ビルド
./gradlew build

# テスト実行
./gradlew test

# アプリケーション実行
./gradlew bootRun

# Docker Compose実行
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