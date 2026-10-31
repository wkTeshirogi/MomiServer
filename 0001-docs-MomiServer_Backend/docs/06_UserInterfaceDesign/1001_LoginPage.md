# Login Page 画面設計書

## 概要

管理画面へのログイン認証を行うページ。メールアドレス/パスワード認証、JWTトークン管理、リダイレクト制御を実装。

---

## 画面構成

### レイアウト構造

```plantuml
@startuml
skinparam componentStyle rectangle
skinparam backgroundColor #FFFFFF

package "Login Page" {
  [Background Container\nmin-h-screen, Centered] as BG
  [Login Card\nmax-w-sm, Rounded-2xl] as Card
}

BG --> Card

package "Login Card" {
  [Logo Section] as Logo
  [Form Section] as Form
}

Card --> Logo
Card --> Form

package "Logo Section" {
  [Icon Container\nPurple Circle + Home Icon] as Icon
  [Title: "SmartHome"] as Title
  [Subtitle: "管理画面にログイン"] as Subtitle
}

package "Form Section" {
  [Error Alert\n(Conditional)] as Error
  [Email Field] as Email
  [Password Field\n+ Toggle Visibility] as Password
  [Submit Button\n+ Loading State] as Submit
}

Form --> Error
Form --> Email
Form --> Password
Form --> Submit
@enduml
```

---

## コンポーネント詳細

### 1. 背景・レイアウト

| 要素 | 仕様 |
|------|------|
| 背景色 | `bg-zinc-50 dark:bg-zinc-950` |
| 配置 | Flexbox 中央揃え (`items-center justify-center`) |
| パディング | `px-4` (モバイル対応) |
| 最小高さ | `min-h-screen` |

### 2. ログインカード

```plantuml
@startuml
skinparam componentStyle rectangle
[Login Card] as LC

package "Card Styles" {
  [Width: w-full max-w-sm] as W
  [Rounded: rounded-2xl] as R
  [Border: border p-8] as B
  [Shadow: shadow-sm] as S
  [Light: bg-white border-zinc-200] as L
  [Dark: dark:bg-zinc-900 dark:border-zinc-800] as D
}

LC --> W
LC --> R
LC --> B
LC --> S
LC --> L
LC --> D
@enduml
```

| スタイル | ライトモード | ダークモード |
|----------|-------------|-------------|
| 背景 | `bg-white` | `dark:bg-zinc-900` |
| 枠線 | `border-zinc-200` | `dark:border-zinc-800` |
| 角丸 | `rounded-2xl` | 共通 |
| 影 | `shadow-sm` | 共通 |
| パディング | `p-8` | 共通 |
| 最大幅 | `max-w-sm` (384px) | 共通 |

### 3. ロゴセクション

```plantuml
@startuml
skinparam componentStyle rectangle
package "Logo Section" {
  [Icon Container] as IC
  [Home Icon (24px, Purple)] as HI
  [Title: "SmartHome"] as T
  [Subtitle: "管理画面にログイン"] as ST
}

IC --> HI
Logo --> IC
Logo --> T
Logo --> ST
@enduml
```

| 要素 | 仕様 |
|------|------|
| アイコン容器 | `rounded-xl bg-purple-500/15 p-3` |
| アイコン | `Home` (lucide-react), 24px, `text-purple-500` |
| タイトル | `text-lg font-semibold text-zinc-900 dark:text-white` |
| サブタイトル | `text-sm text-zinc-500`, "管理画面にログイン" |
| 間隔 | `mb-8`, `gap-3` |

### 4. フォームセクション

```plantuml
@startuml
skinparam componentStyle rectangle
package "Form Section" {
  [Form onSubmit=handleSubmit] as F
  [Error Alert (Conditional)] as EA
  [Email Field Group] as EF
  [Password Field Group] as PF
  [Submit Button] as SB
}

F --> EA
F --> EF
F --> PF
F --> SB
@enduml
```

#### 4.1 エラーアラート (条件付き表示)

```plantuml
@startuml
skinparam componentStyle rectangle
[Error State] as ES

package "Error Alert Styles" {
  [Light: border-red-200 bg-red-50 text-red-600] as LE
  [Dark: dark:border-red-900 dark:bg-red-950/50 dark:text-red-400] as DE
  [Rounded: rounded-lg] as R
  [Padding: px-4 py-3] as P
  [Text: text-sm] as T
}

ES --> LE
ES --> DE
ES --> R
ES --> P
ES --> T
@enduml
```

| 状態 | スタイル |
|------|----------|
| 表示条件 | `error &&` (error state が存在する場合) |
| ライト | `border-red-200 bg-red-50 text-red-600` |
| ダーク | `dark:border-red-900 dark:bg-red-950/50 dark:text-red-400` |
| 共通 | `rounded-lg px-4 py-3 text-sm` |

#### 4.2 メールアドレスフィールド

```plantuml
@startuml
skinparam componentStyle rectangle
package "Email Field" {
  [Label: "メールアドレス"] as L
  [Input: type=email] as I
  [Placeholder: "admin@example.com"] as PH
}

package "Input Styles" {
  [Base: w-full rounded-lg border px-3 py-2 text-sm] as B
  [Light: bg-white border-zinc-300 text-zinc-900] as L
  [Dark: dark:bg-zinc-800 dark:border-zinc-700 dark:text-zinc-100] as D
  [Focus: focus:border-purple-500 focus:ring-1 focus:ring-purple-500] as F
  [Placeholder: placeholder:text-zinc-400 dark:placeholder:text-zinc-500] as PH
}
@enduml
```

| 属性 | 値 |
|------|-----|
| `id` | "email" |
| `type` | "email" |
| `autoComplete` | "email" |
| `required` | true |
| `placeholder` | "<admin@example.com>" |
| ラベル | "メールアドレス" |

#### 4.3 パスワードフィールド (表示切替付き)

```plantuml
@startuml
skinparam componentStyle rectangle
package "Password Field" {
  [Label: "パスワード"] as L
  [Input Wrapper: relative] as W
  [Input: type=password|text] as I
  [Toggle Button: Eye/EyeOff] as TB
}

package "Toggle Button" {
  [Position: absolute right-3 top-1/2 -translate-y-1/2] as P
  [Icon: Eye (hidden) / EyeOff (visible)] as IC
  [Color: text-zinc-400 hover:text-zinc-600 dark:hover:text-zinc-300] as C
  [Aria-label: 表示/非表示切替] as AL
}
@enduml
```

| 要素 | 仕様 |
|------|------|
| `id` | "password" |
| `type` | `showPass ? 'text' : 'password'` |
| `autoComplete` | "current-password" |
| `required` | true |
| `placeholder` | "••••••••" |
| ラベル | "パスワード" |
| 表示切替ボタン | 右端配置、Eye/EyeOff アイコン切替 |

#### 4.4 送信ボタン

```plantuml
@startuml
skinparam componentStyle rectangle
package "Submit Button" {
  [Button: type=submit] as B
  [Loading Spinner: Loader2] as LS
  [Text: "ログイン" / "ログイン中..."] as T
}

package "Button States" {
  [Default: bg-purple-600 text-white] as D
  [Hover: hover:bg-purple-500] as H
  [Disabled: disabled:opacity-60 cursor-not-allowed] as DS
  [Loading: animate-spin Loader2 + "ログイン中..."] as LS
}
@enduml
```

| 状態 | スタイル |
|------|----------|
| デフォルト | `bg-purple-600 text-white` |
| ホバー | `hover:bg-purple-500` |
| 無効 | `disabled:opacity-60 disabled:cursor-not-allowed` |
| ローディング | `Loader2` (animate-spin) + "ログイン中..." |
| 通常時 | "ログイン" |
| 幅 | `w-full` |
| 高さ | `py-2.5` |
| 角丸 | `rounded-lg` |

---

## 状態管理

```plantuml
@startuml
skinparam componentStyle rectangle
package "State Variables" {
  [email: string] as E
  [password: string] as P
  [showPass: boolean] as SP
  [loading: boolean] as L
  [error: string | null] as ER
}

package "External Dependencies" {
  [useAuth: login, isAuthenticated] as UA
  [useNavigate: navigate] as UN
  [useLocation: location] as UL
  [loginApi: OpenAPI Client] as LA
}
@enduml
```

| State | 型 | 初期値 | 用途 |
|-------|-----|--------|------|
| `email` | string | `''` | メールアドレス入力値 |
| `password` | string | `''` | パスワード入力値 |
| `showPass` | boolean | `false` | パスワード表示/非表示 |
| `loading` | boolean | `false` | 送信中フラグ (ボタン制御) |
| `error` | string \| null | `null` | エラーメッセージ表示 |

---

## 認証フロー

```plantuml
@startuml
skinparam componentStyle rectangle

actor User
[LoginPage] as LP
[AuthContext] as AC
[loginApi] as API
[LocalStorage] as LS
[Router] as RT

User -> LP: メール/パスワード入力
User -> LP: [ログイン] クリック
LP -> LP: setLoading(true), setError(null)
LP -> API: loginApi({ email, password })
API --> LP: { accessToken, refreshToken }
LP -> LP: JWTから role 抽出 (atob)
LP -> AC: login({ email, role }, accessToken, refreshToken)
AC -> LS: トークン保存
AC -> AC: setUser(user)
LP -> RT: navigate(from, { replace: true })
RT -> User: リダイレクト完了

note right of LP
  エラー時:
  401: "メールアドレスまたはパスワードが正しくありません"
  400: "入力内容に誤りがあります"
  その他: "サーバーエラーが発生しました..."
end note
@enduml
```

### エラーハンドリング

| HTTP Status | エラーメッセージ |
|-------------|------------------|
| 401 | "メールアドレスまたはパスワードが正しくありません" |
| 400 | "入力内容に誤りがあります" |
| その他 | "サーバーエラーが発生しました。しばらくしてから再試行してください" |

### リダイレクト制御

```plantuml
@startuml
skinparam componentStyle rectangle
[LoginPage] as LP
[useLocation] as UL
[useNavigate] as UN
[AuthContext] as AC

UL -> LP: location.state.from.pathname
AC -> LP: isAuthenticated チェック
LP -> UN: navigate(from, { replace: true })
note right of LP
  認証済みなら即座にリダイレクト
  from がない場合は '/' (root)
end note
@enduml
```

| 条件 | 挙動 |
|------|------|
| `isAuthenticated === true` | `<Navigate to={from} replace />` 即座にリダイレクト |
| `from` 未指定 | `'/'` (ルート) にリダイレクト |
| ログイン成功 | `navigate(from, { replace: true })` |

---

## API 連携

```plantuml
@startuml
skinparam componentStyle rectangle
[LoginPage] as LP
[loginApi] as LA
[OpenAPI Generated Client] as OG

LP -> LA: loginApi({ body: { email, password }, throwOnError: true })
LA -> OG: POST /auth/login
OG --> LA: { data: { accessToken, refreshToken } }
LA --> LP: res.data

note right of LP
  accessToken: JWT (role含む)
  refreshToken: リフレッシュ用
  両方必須 (存在チェックあり)
end note
@enduml
```

### リクエスト

```typescript
await loginApi({
  body: { email, password },
  throwOnError: true,  // エラー時に例外スロー
})
```

### レスポンス

```typescript
{
  data: {
    accessToken: string,   // JWT (role含む)
    refreshToken: string   // リフレッシュトークン
  }
}
```

### JWT ペイロード解析

```typescript
const payload = JSON.parse(atob(accessToken.split('.')[1]))
role = payload.role ?? 'USER_ROLE'
```

---

## アクセシビリティ

| 要素 | 対応 |
|------|------|
| ラベル | `<label htmlFor="email/password">` 明示的関連付け |
| 必須項目 | `required` 属性 |
| オートコンプリート | `autoComplete="email" / "current-password"` |
| パスワード表示切替 | `aria-label` で "パスワードを表示/隠す" |
| フォーカス表示 | `focus:border-purple-500 focus:ring-1 focus:ring-purple-500` |
| エラー表示 | インラインアラート (色のみでなくテキストで通知) |
| ローディング | ボタン無効化 + スピナー + テキスト変更 |

---

## レスポンシブ対応

```plantuml
@startuml
skinparam componentStyle rectangle
package "Responsive" {
  [Mobile: px-4, カード幅100%] as M
  [Tablet/Desktop: max-w-sm (384px) 固定] as T
}
@enduml
```

| ブレークポイント | カード幅 | パディング |
|------------------|----------|------------|
| Mobile (< 640px) | `w-full` (px-4 で余白確保) | `px-4` |
| ≥ sm | `max-w-sm` (384px) 中央配置 | `px-4` |

---

## 実装ファイル

```text
src/pages/Top/
└── LoginPage.tsx  # 163行
```

### インポート依存

```plantuml
@startuml
skinparam componentStyle rectangle
[LoginPage] as LP

[React: useState] as RS
[React Router: useNavigate, useLocation, Navigate] as RR
[Lucide: Home, Eye, EyeOff, Loader2] as LI
[Utils: cn] as UT
[AuthContext: useAuth] as AC
[SDK: loginApi] as SDK

LP --> RS
LP --> RR
LP --> LI
LP --> UT
LP --> AC
LP --> SDK
@enduml
```

| インポート | 用途 |
|------------|------|
| `useState` | フォーム状態管理 |
| `useNavigate, useLocation, Navigate` | ルーティング制御 |
| `Home, Eye, EyeOff, Loader2` | アイコン |
| `cn` | クラス名結合ユーティリティ |
| `useAuth` | 認証コンテキスト (login, isAuthenticated) |
| `loginApi` | OpenAPI生成ログインAPI |

---

## セキュリティ考慮事項

| 対策 | 実装 |
|------|------|
| パスワード非表示 | `type="password"` デフォルト |
| 入力値サニタイズ | `throwOnError: true` でAPI側バリデーション |
| トークン保存 | `localStorage` (AuthContext内で管理) |
| JWT検証 | ペイロードから role 抽出 (クライアント側) |
| CSRF対策 | SameSite Cookie / トークンベース認証で緩和 |
| ブルートフォース | サーバー側でレート制限推奨 |

---

## 今後の拡張ポイント

1. **ソーシャルログイン**: Google, GitHub, Microsoft 等のOAuth追加
2. **多要素認証 (MFA)**: TOTP, SMS, Email 認証コード
3. **パスワードレス**: Magic Link, WebAuthn (Passkeys)
4. **リメンバー機能**: "ログイン状態を保持" チェックボックス
5. **パスワード強度表示**: 入力時のリアルタイム強度インジケータ
6. **多言語対応**: i18n ライブラリ導入
7. **アカウントロックアウト**: 失敗回数超過時の一時ロック表示
