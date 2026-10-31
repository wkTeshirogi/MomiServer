# フロントエンド権限制御設計

## 概要

フロントエンドでは、バックエンドの認可モデルに合わせて画面遷移、メニュー表示、部品表示を制御する。

ただし、フロントエンドの制御は利用者体験を整えるための補助であり、最終的なアクセス可否はバックエンドの認可アノテーションで判定する。

## 制御対象

| 制御単位 | 実装 | 目的 |
| --- | --- | --- |
| ルート | `RoleRoute` | URL直打ちによる管理画面表示を抑止する |
| サイドバー | `canAccessAdmin` | 権限のないメニューを表示しない |
| 画面 | `Navigate` | 権限なし状態で画面に入った場合に退避する |
| 部品 | `RequireRole` / `RequireBatchExecution` | ボタンや操作部品を権限に応じて出し分ける |

## ロール正規化

バックエンドのJWTやAPIレスポンスでは、ロール表現に以下の揺れが発生し得る。

| 入力例 | 正規化後 |
| --- | --- |
| `ADMIN` | `ADMIN` |
| `ROLE_ADMIN` | `ADMIN` |
| `ADMIN_ROLE` | `ADMIN` |
| `USER` | `USER` |
| `BAN_USER` | `BAN_USER` |

フロントエンドでは共通関数でロールを正規化し、画面ごとに個別実装しない。
未指定または未知の値は `BAN_USER` として扱い、利用可能側へ補正しない。

```ts
const role = normalizeRole(payload.role)

if (role === 'ADMIN') {
  // 管理者向けの導線を表示する
}
```

## ルート制御

管理画面配下は `ADMIN` のみ遷移可能とする。

```tsx
<Route element={<RoleRoute allowedRoles={['ADMIN']} />}>
  <Route path="/master" element={<MasterList />} />
  <Route path="/master/users" element={<UserMasterList />} />
</Route>
```

`RoleRoute` は権限を満たさない場合、トップ画面へ戻す。

```tsx
if (!hasAnyRole(user, allowedRoles)) {
  return <Navigate to="/index" replace />
}
```

### 画面ルート

| 画面 | URL | ADMIN | OPERATOR | USER |
| --- | --- | --- | --- | --- |
| トップ | `/index` | 可 | 可 | 可 |
| 個人設定 | `/settings` | 本人のみ | 本人のみ | 本人のみ |
| マシンステータス | `/machine/status`、`/machine/status/:serverId` | 可 | 可 | 不可 |
| サービスステータス | `/service/status`、`/service/status/:serverId` | 可 | 可 | 可 |
| アラート | `/alerts` | 可 | 可 | 可 |
| APIログ | `/logs/api` | 可 | 可 | 不可 |
| 在庫管理 | `/stock`、`/stock/:itemId`、互換URL | 可 | 可 | 可 |
| 車両管理一覧・詳細 | `/vehicles`、`/vehicles/:vehicleId` | 可 | 可 | 閲覧のみ |
| 車両登録・取込 | `/vehicles/new`、`/vehicles/import/**` | 可 | 可 | 不可 |
| マスタ管理 | `/master/**` | 可 | 不可 | 不可 |

## メニュー表示制御

サイドバーでは、管理者権限がないユーザーに「マスタ管理」を表示しない。
`USER` にはマシンステータスとAPIログを表示しない。未実装のダッシュボード、ドキュメント、
APIリファレンスは全ロールで表示しない。

```tsx
const visibleItems = items.filter(item =>
  item.href !== '/master' || canAccessAdmin(user)
)
```

### 外部システム導線

| 導線 | ADMIN | OPERATOR | USER |
| --- | --- | --- | --- |
| GitLab | 表示 | 表示 | 非表示 |
| Grafana | 表示 | 表示 | 非表示 |
| Prometheus | 表示 | 表示 | 非表示 |
| Swagger UI | 表示 | 非表示 | 非表示 |

導線の表示可否は外部システム内の認証を代替しない。Swagger UIの直接アクセス可否は
バックエンドのOpenAPI公開設定に従う。

## 部品単位制御

画面内の保存ボタン、実行ボタン、削除ボタンなどは、共通コンポーネントで囲って表示制御する。

```tsx
<RequireRole allowedRoles={['ADMIN']}>
  <Button>保存</Button>
</RequireRole>
```

バッチ実行ボタンは、バッチ定義の `required_role` を使用して表示可否を判定する。

```tsx
<RequireBatchExecution requiredRole={batchDefinition.required_role}>
  <Button>実行</Button>
</RequireBatchExecution>
```

バッチ権限は `ADMIN > OPERATOR > USER` の階層で判定する。`ADMIN` は管理者上書きを持つ。
未設定、未知、`BAN_USER` の `required_role` は表示しない。バッチ取消ボタンは `ADMIN` のみに表示する。

マシンステータスとサービスステータスの設定ボタンは `ADMIN` のみに表示する。

車両管理は全ロールに閲覧を許可し、登録・編集・PDFまたは画像取込はADMINとOPERATORに限定する。
管理終了または削除操作はADMINだけに表示する。

## バックエンドとの関係

| 項目 | フロントエンド | バックエンド |
| --- | --- | --- |
| 管理者画面 | `RoleRoute` とメニュー非表示 | `@AdminOnly` |
| 手動バッチ実行 | `RequireBatchExecution` | `@CanExecuteBatch` |
| 認証切れ | `PrivateRoute` とトークン同期 | JWT認証フィルタ |
| 最終的な拒否 | 表示・遷移抑止 | 401 / 403 応答 |

## 認証状態の復元・失効

AuthContextは保存済みアクセストークン・更新トークン・メールアドレスが揃った場合に認証状態を復元する。
両トークンのexpを確認し、期限切れ・exp欠落・解析不能、またはBAN_USERなら復元しない。
クライアントでのJWT解析は署名検証ではなく、最終判定はバックエンドが行う。

PrivateRouteは経路・クエリ・ハッシュの変更時に保存情報を再同期する。
認証情報が無効なら保存情報を削除する。両トークンの早い方の期限でも再同期する。
未認証時は元の遷移先をstateに保持して/loginへ戻し、ADMIN・OPERATOR・USER以外も/loginへ戻す。
この復元処理自体はトークン更新を実行しない。APIクライアントの401時更新とは区別する。
ログアウト要求の成否にかかわらず、ローカル認証情報と画面上の認証状態を破棄する。

## 実装時の注意

- フロントエンドの表示制御だけでセキュリティを完結させない。
- `BAN_USER` は常に許可対象から除外する。
- 新しい管理画面を追加する場合は、ルートとサイドバーの両方で権限制御を確認する。
- 新しい操作ボタンを追加する場合は、操作種別に応じて `RequireRole` または専用の表示制御コンポーネントを使用する。

## 関連ドキュメント

- [認可設計](../11_SecurityDesign/0002_authorization.md)
- [認可アノテーション実装設計](../11_SecurityDesign/0005_authorization_annotation.md)
- [バッチ定義画面設計書](./3001_batch_definition.md)
- [車両管理画面設計書](./3008_vehicle_management.md)
