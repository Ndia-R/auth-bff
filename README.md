# Auth BFF (Backend for Frontend)

これは、Keycloakとの認証フローを処理するBackend for Frontend (BFF)のためのSpring Bootアプリケーションです。

## プロジェクト構造

- **Controller層**: `AuthController`が認証エンドポイントを処理
- **設定**:
  - `SecurityConfig` - OAuth2とセキュリティ設定
  - `RedisConfig` - Redisセッション管理設定
- **認証フロー**: KeycloakでのOAuth2 Authorization Codeフロー

## 主要機能

- KeycloakとのOAuth2連携
- Redisベースのセッション管理
- RESTful認証エンドポイント
- フロントエンド連携のためのCORS設定
- HttpOnly、Secure、SameSite属性による安全なCookie処理

## エンドポイント

- `GET /bff/auth/login` - 認証状態とユーザー情報、アクセストークンの取得
- `POST /bff/auth/logout` - ログアウトとセッションクリア
- `GET /bff/auth/user` - 現在のユーザー情報取得
- `POST /bff/auth/refresh` - アクセストークンのリフレッシュ
- `GET /bff/auth/health` - ヘルスチェックエンドポイント

## 設定

アプリケーションは以下の主要設定ファイルを使用します：

- `application.yml` - メインアプリケーション設定
- `.env.example` - 環境変数テンプレート

### 必要な環境変数

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

## ビルドと実行

```bash
# アプリケーションのビルド
./gradlew build -x test

# アプリケーションの実行
./gradlew bootRun

# またはDocker Composeで実行
docker compose up
```

## 開発セットアップ

プロジェクトには以下が含まれています：
- Javaフォーマット設定を含むVS Code設定
- Gradleビルド設定
- RedisでのDocker Compose設定
- テスト設定（OAuth2モックの追加設定が必要な場合があります）

## アーキテクチャ

このBFFは提供されたシーケンス図に従います：

1. フロントエンドが`/bff/auth/login`経由で認証を要求
2. BFFがOAuth2 authorization code flowのためKeycloakにリダイレクト
3. 認証成功後、アクセストークンが返される
4. セッションがRedisに保存され、安全なCookieが設定される
5. フロントエンドはAPIコール用にアクセストークンを使用可能
6. トークンリフレッシュは`/bff/auth/refresh`で処理
7. ログアウトはセッションとCookieをクリア