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

1. **BFFパターン実装**: フロントエンドはトークンを一切扱わず、BFFがすべてのAPI呼び出しをプロキシ
   - XSS攻撃からトークンを完全に保護
   - セッションCookieのみでAPIアクセス可能

2. **PKCE対応**: Authorization Code with PKCEによるセキュアなOAuth2認証

3. **最小構成**: 13ファイルで構成された、保守しやすいシンプルな設計
   - 未使用のクラス・メソッドは一切なし
   - Spring Boot自動設定を最大限活用

4. **完全なCSRF保護**: CookieベースのCSRFトークンで状態変更操作を保護

5. **OpenID Connect準拠**: RP-Initiated Logoutによる確実なKeycloakセッション無効化

6. **権限制御の委譲**: BFFは認証とトークン管理に専念、権限制御はリソースサーバーで実施

## アーキテクチャ

### 認証フロー（PKCE対応）
```
フロントエンド (SPA)
    ↓ Cookie: BFFSESSIONID + XSRF-TOKEN
   BFF (APIゲートウェイ)
    ├─ 認証管理 (/bff/auth/*)
    ├─ APIプロキシ (/api/**)  ← すべてのAPIを透過的にプロキシ
    └─ トークン管理（Redisセッション）
    ↓ Authorization: Bearer <access_token>
リソースサーバー (API)
    ├─ ビジネスロジック
    ├─ 権限制御  ← 権限チェックはここで実施
    └─ データ処理
```

### 主要コンポーネント（最小構成・13ファイル）

#### アプリケーション
- **AuthBffApplication**: メインクラス

#### 設定 (config/)
- **WebClientConfig**: 共有WebClient設定（シングルトン、タイムアウト管理）
- **CsrfCookieFilter**: CSRF Cookie自動設定フィルター
- **RedisConfig**: Spring Session Data Redis設定
- **SecurityConfig**: Spring Security設定（PKCE、CSRF保護、CORS）

#### コントローラー (controller/)
- **ApiProxyController**: すべてのAPIリクエストをプロキシ（175行、1メソッドのみ）
- **AuthController**: 認証エンドポイント（ログイン・ログアウト・ユーザー情報）

#### DTO (dto/)
- **ErrorResponse**: 統一エラーレスポンス
- **LogoutResponse**: ログアウトレスポンス
- **UserResponse**: ユーザー情報レスポンス

#### 例外 (exception/)
- **GlobalExceptionHandler**: 統一エラーハンドリング
- **UnauthorizedException**: 認証エラー例外

#### サービス (service/)
- **AuthService**: 認証ビジネスロジック（ログアウト、ユーザー情報取得）

## エンドポイント

### 認証エンドポイント

| メソッド | パス | 説明 | レスポンス |
|---------|------|------|-----------|
| GET | `/bff/auth/login` | 認証状態確認・OAuth2フロー開始 | リダイレクト |
| GET | `/bff/auth/user` | 現在のユーザー情報取得 | `UserResponse` |
| POST | `/bff/auth/logout` | 通常ログアウト（BFFセッションのみクリア） | `LogoutResponse` |
| POST | `/bff/auth/logout?complete=true` | 完全ログアウト（Keycloakセッションも無効化） | `LogoutResponse` |
| GET | `/actuator/health` | ヘルスチェック | Spring Boot Actuator標準レスポンス |

### APIプロキシエンドポイント

| メソッド | パス | 説明 | 転送先 |
|---------|------|------|--------|
| GET/POST/PUT/DELETE/PATCH | `/api/**` | すべてのAPIリクエストをプロキシ | `${RESOURCE_SERVER_URL}/**` |

**重要な設計方針**:
- BFFは `/api/**` 配下のすべてのリクエストを透過的にプロキシ
- 認証済みユーザーのアクセストークンを自動的に付与
- **権限制御はリソースサーバー側で実施**（BFFは権限チェックなし）
- 新しいAPIエンドポイント追加時、BFFのコード変更は不要

**プロキシの動作例**:
```
フロントエンド                BFF                     リソースサーバー
GET /api/books/list    →  GET /books/list     (トークン付与)
POST /api/music        →  POST /music         (トークン付与)
GET /api/admin/users   →  GET /admin/users    (トークン付与、権限チェックはリソースサーバー側)
```

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
```json
{
  "message": "success"
}
```

**ログアウトの種類:**
- **通常ログアウト** (`complete=false` または省略): BFFセッションのみクリア
- **完全ログアウト** (`complete=true`): BFFセッション + Keycloakセッションをクリア

### ErrorResponse
統一的なエラーレスポンス形式。

```json
{
  "error": "UNAUTHORIZED",
  "message": "認証が必要です",
  "status": 401,
  "path": "/bff/auth/login",
  "timestamp": "2025-10-12 14:30:45"
}
```

#### WebClient/Keycloak通信特有のエラーコード
| エラーコード | HTTPステータス | 説明 |
|-------------|---------------|------|
| `KEYCLOAK_CLIENT_ERROR` | 400 | Keycloak通信でクライアントエラー |
| `KEYCLOAK_SERVER_ERROR` | 503 | Keycloak サーバーエラー |
| `KEYCLOAK_CONNECTION_ERROR` | 503 | Keycloak接続エラー |

## セキュリティ設定

### CSRF保護
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
    'X-XSRF-TOKEN': csrfToken
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

## APIプロキシの詳細実装

### 主要機能

1. **UriBuilderによる安全なURI構築**
   - クエリパラメータの自動エンコード
   - パスの重複スラッシュ問題を解消

2. **Content-Type転送**
   - フロントエンドのContent-Typeをリソースサーバーに正しく転送
   - POST/PUTリクエストで自動適用

3. **レスポンスヘッダーフィルタリング**
   - Springが自動設定するヘッダーを除外
   - 除外対象: `transfer-encoding`, `connection`, `keep-alive`, `upgrade`, `server`, `content-length`

4. **タイムアウト設定**
   - 30秒タイムアウト
   - リソースサーバーの応答遅延時に自動でエラー返却

5. **透過的なレスポンス転送**
   - ステータスコード保持（404, 500等）
   - カスタムヘッダー転送（`X-Total-Count`等）
   - Content-Type保持

## 環境変数

### 基本設定
```bash
# ============================================
# Keycloak Configuration
# ============================================
KEYCLOAK_CLIENT_ID=my-books-client
KEYCLOAK_CLIENT_SECRET=your-client-secret
KEYCLOAK_REDIRECT_URI=http://localhost:8888/bff/login/oauth2/code/keycloak

# 外部Keycloak使用（ISSUER_URIで統一）
KEYCLOAK_ISSUER_URI=https://auth.localhost/realms/test-user-realm
KEYCLOAK_POST_LOGOUT_REDIRECT_URI=http://localhost:5173/logout-complete

# ============================================
# Redis Configuration
# ============================================
REDIS_HOST=redis
REDIS_PORT=6379

# ============================================
# Application Configuration
# ============================================
# フロントエンドURL
FRONTEND_URL=http://localhost:5173

# リソースサーバーURL
RESOURCE_SERVER_URL=http://api.example.com

# CORS許可オリジン（カンマ区切り）
CORS_ALLOWED_ORIGINS=http://localhost:5173,http://localhost:*

# リソースサーバータイムアウト（秒、デフォルト: 30）
RESOURCE_SERVER_TIMEOUT=30
```

**重要**: `KEYCLOAK_ISSUER_URI`を使用することで、Spring Securityが自動的に各エンドポイント（authorize, token, jwk, logout）を検出します。個別エンドポイント指定は不要です。

## 開発環境

### 環境概要
このプロジェクトは **WSL2上のUbuntu** で **VSCode DevContainer** + **Docker Compose** を使用して開発しています。

```
WSL2 (Ubuntu) → VSCode DevContainer → Docker Compose
                                          ├── auth-bff (開発コンテナ)
                                          └── redis (セッションストレージ)
```

**注意**: Keycloakは外部の認証サーバー（`https://auth.localhost`）を使用します。

### DevContainer構成

#### 基本情報
- **コンテナ名**: `auth-bff`
- **ベースイメージ**: `eclipse-temurin:17-jdk-jammy`
- **実行ユーザー**: `vscode`
- **作業ディレクトリ**: `/workspace`

#### インストール済みツール
| ツール | バージョン | 用途 |
|--------|-----------|------|
| Java (Eclipse Temurin) | 17 | アプリケーション実行環境 |
| Gradle | ラッパー経由 | ビルドツール |
| Git | 最新 | バージョン管理 |

#### 永続化ボリューム
| ボリューム名 | マウント先 | 用途 |
|------------|-----------|------|
| (プロジェクトディレクトリ) | `/workspace` | ソースコード |
| `gradle-cache` | `/home/vscode/.gradle` | Gradleキャッシュ |
| `claude-config` | `/home/vscode/.claude` | Claude Code設定・認証情報 |

### Docker Compose サービス構成

#### 1. auth-bff (開発コンテナ)
```yaml
ports: 8888:8080
networks: shared-network
depends_on: [redis]
```

#### 2. redis (セッションストレージ)
```yaml
image: redis:8.2
ports: 6379:6379
networks: shared-network
```

### ネットワーク構成
```
外部ブラウザ
    ↓ http://localhost:8888
auth-bff:8080 ←→ redis:6379
    ↓ https://auth.localhost (外部Keycloak)
Keycloak (外部認証サーバー)
```

**重要**: Keycloakは外部サーバー（`https://auth.localhost`）を使用します。`KEYCLOAK_ISSUER_URI`環境変数で接続先を指定してください。

### 開発環境起動

```bash
# Docker Compose環境起動
docker compose up -d

# VSCodeでDevContainer接続
code .
# VSCodeコマンドパレット: "Dev Containers: Reopen in Container"

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

### 📁 ソースコード構成（13ファイル）

```
src/main/java/com/example/auth_bff/
├── AuthBffApplication.java              # メインクラス
│
├── config/                              # 設定（4ファイル）
│   ├── WebClientConfig.java             # 共有WebClient設定（シングルトン）
│   ├── CsrfCookieFilter.java           # CSRF Cookie自動設定フィルター
│   ├── RedisConfig.java                 # Redis/Spring Session設定
│   └── SecurityConfig.java              # Spring Security + PKCE + CORS
│
├── controller/                          # コントローラー（2ファイル）
│   ├── ApiProxyController.java          # APIプロキシ（/api/**、175行、1メソッド）
│   └── AuthController.java              # 認証エンドポイント（/bff/auth/*）
│
├── dto/                                 # DTO（3ファイル）
│   ├── ErrorResponse.java               # 統一エラーレスポンス
│   ├── LogoutResponse.java              # ログアウトレスポンス
│   └── UserResponse.java                # ユーザー情報レスポンス
│
├── exception/                           # 例外（2ファイル）
│   ├── GlobalExceptionHandler.java      # 統一エラーハンドラー
│   └── UnauthorizedException.java       # 認証エラー例外
│
└── service/                             # サービス（1ファイル）
    └── AuthService.java                 # 認証ビジネスロジック
```

### 🎯 設計原則

1. **必要最小限の構成**: すべてのクラスとメソッドが実際に使用されている
2. **BFFパターン**: フロントエンドはトークンを一切扱わない
3. **権限制御の委譲**: BFFは認証に専念、権限はリソースサーバーが管理
4. **Spring Boot自動設定の活用**: カスタムBean最小限
5. **シンプルなエラーハンドリング**: 実際に発生する例外のみ処理
6. **1メソッドプロキシ**: ApiProxyControllerは175行、1メソッドのみ
7. **シンプルな設定管理**: @Valueアノテーションで直接的に設定値を取得
8. **WebClientシングルトン**: コネクションプール再利用によるパフォーマンス向上

## 開発時の注意点

### コーディングスタイル
- **早期例外**: null チェック後即座に例外をスロー
- **型安全**: 具体的なDTOクラスを使用
- **単一責任**: Controller/Service の明確な分離
- **必要最小限**: 未使用のクラス・メソッドは作らない
- **アノテーション活用**: `@NonNull` で明示的なnull制約

### エラーハンドリング
- 認証エラー: `UnauthorizedException`
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

4. **Keycloak接続問題**
   - `KEYCLOAK_ISSUER_URI`が正しく設定されているか確認
   - 外部Keycloakサーバーにアクセス可能か確認

## API使用例（BFFパターン）

### 認証フロー

```javascript
// 1. ログイン開始
window.location.href = '/bff/auth/login';

// 2. ユーザー情報取得
fetch('/bff/auth/user', {
  credentials: 'include'
})
  .then(response => response.json())
  .then(user => console.log(user.name));

// 3. 完全ログアウト
fetch('/bff/auth/logout?complete=true', {
  method: 'POST',
  credentials: 'include'
})
  .then(() => window.location.href = '/');
```

### APIプロキシの使用

```javascript
// CSRFトークン取得
function getCsrfToken() {
  return document.cookie
    .split('; ')
    .find(row => row.startsWith('XSRF-TOKEN='))
    ?.split('=')[1];
}

// GET リクエスト
fetch('/api/books/list', {
  credentials: 'include'
})
  .then(response => response.json())
  .then(books => console.log(books));

// POST リクエスト
fetch('/api/books', {
  method: 'POST',
  credentials: 'include',
  headers: {
    'Content-Type': 'application/json',
    'X-XSRF-TOKEN': getCsrfToken()
  },
  body: JSON.stringify({ title: 'Spring Security実践ガイド' })
})
  .then(response => response.json())
  .then(book => console.log('作成:', book));
```

**重要**: BFFは `/api/**` 配下のすべてのリクエストを自動的にプロキシします。新しいエンドポイント追加時、BFF側のコード変更は不要です。

---

🚀 **Ready for Production!** このBFFは本番環境でのOAuth2認証フローに対応しています。
