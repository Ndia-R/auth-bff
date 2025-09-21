# Keycloak Realm Configuration (realm-export.json)

このファイルは、Keycloakレルム「test-user-realm」の設定とテストデータを定義しています。

## レルム基本設定

| 項目 | 値 | 説明 |
|------|----|----|
| `id` | test-user-realm | レルムの一意識別子 |
| `realm` | test-user-realm | レルム名 |
| `enabled` | true | レルムが有効かどうか |
| `sslRequired` | external | SSL要件（external: 外部接続時のみSSL必須） |
| `registrationAllowed` | false | ユーザー自己登録を許可するか |
| `loginWithEmailAllowed` | true | メールアドレスでのログインを許可するか |
| `duplicateEmailsAllowed` | false | 重複メールアドレスを許可するか |
| `resetPasswordAllowed` | true | パスワードリセットを許可するか |
| `editUsernameAllowed` | false | ユーザー名の編集を許可するか |

## クライアント設定

### my-books-client クライアント

SPAアプリケーション用のOAuth2/OpenID Connectクライアントです。

| 項目 | 値 | 説明 |
|------|----|----|
| `clientId` | my-books-client | クライアント識別子 |
| `enabled` | true | クライアントが有効かどうか |
| `publicClient` | false | 機密クライアント（シークレットあり） |
| `secret` | your-client-secret | クライアントシークレット（開発用） |
| `protocol` | openid-connect | 認証プロトコル |
| `directAccessGrantsEnabled` | true | リソースオーナーパスワード認証を許可 |
| `standardFlowEnabled` | true | 認証コードフローを許可 |
| `implicitFlowEnabled` | false | インプリシットフローを無効 |
| `serviceAccountsEnabled` | false | サービスアカウントを無効 |
| `authorizationServicesEnabled` | false | 認可サービスを無効 |

#### リダイレクトURI
- `http://localhost:5173/*` - 開発サーバー用
- `http://localhost:8080/auth/callback` - 認証コールバック用

## テストユーザー

開発・テスト用に10人のユーザーが定義されています。

### 共通設定
- **パスワード**: `abc`
- **メール認証**: 済み
- **アカウント状態**: 有効

### ユーザー一覧

| ユーザー名 | メールアドレス | ロール |
|-----------|---------------|--------|
| Lars | lars@gmail.com | user |
| Nina | nina@gmail.com | user |
| Paul | paul@gmail.com | user, **admin** |
| Julia | julia@gmail.com | user, **admin** |
| Eddy | lee@gmail.com | user |
| Lili | lili@gmail.com | user |
| Steve | steve@gmail.com | user |
| Anna | anna@gmail.com | user |
| Law | law@gmail.com | user |
| Alisa | alisa@gmail.com | user |

### 管理者権限
- **Paul**と**Julia**のみが `admin` ロールを持っています
- 他のユーザーは `user` ロールのみ

## ロール定義

- `user`: 一般ユーザーロール
- `admin`: 管理者ロール

## 使用方法

1. Docker Composeでkeycloakを起動
2. このファイルが自動的にインポートされる
3. 管理コンソール: http://localhost:8180/admin
4. 管理者ログイン: admin/admin

## セキュリティ注意事項

⚠️ **このファイルは開発・テスト用です**
- クライアントシークレットはプレースホルダー
- ユーザーパスワードは弱い設定
- 本番環境では使用しないでください