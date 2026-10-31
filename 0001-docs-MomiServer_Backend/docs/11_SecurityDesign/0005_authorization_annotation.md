# 認可アノテーション実装設計

## 概要

バックエンドの認可制御は、Spring Security のメソッドセキュリティを直接 Controller に記載するのではなく、用途別の専用アノテーションへ集約する。

これにより、Controller には「どの権限式で守るか」ではなく「どの操作種別として守るか」を表現させる。

## 対象

| アノテーション | 用途 | 主な適用先 |
| --- | --- | --- |
| `@AdminOnly` | 管理者のみ許可する | マスタ管理API、管理者向け登録API |
| `@CanExecuteBatch` | バッチ定義単位の実行権限を判定する | 手動バッチ実行API |
| `@CanReadBatchExecution` | 実行要求に紐づく定義の参照権限を判定する | バッチ実行詳細・ログ参照API |

## 実装方針

### 管理者限定制御

管理者限定APIでは、共通アノテーションに `ADMIN` ロール判定を持たせる。

Controller 側は `@PreAuthorize` の式を直接持たず、`@AdminOnly` のみを宣言する。

```java
@AdminOnly
@RequestMapping("/api/master/user")
public class MasterUserController {
}
```

### バッチ実行制御

手動バッチ実行では、API全体を単純に `ADMIN` 固定にはせず、バッチ定義の `required_role` を参照して実行可否を判定する。

```java
@CanExecuteBatch
@PostMapping("/definitions/{batchDefinitionId}")
public ResponseEntity<?> createManualExecution(@PathVariable Long batchDefinitionId) {
}
```

`@CanExecuteBatch` は対象メソッドの第1引数をバッチ定義IDとして扱う。
Javaコンパイル時のパラメータ名保持設定に依存しないよう、判定式では引数名ではなく位置指定を使用する。

```java
@PreAuthorize("@batchPermissionService.canExecute(authentication, #p0)")
```

### バッチ実行詳細・ログ参照制御

実行IDから実行要求に紐づくバッチ定義の `required_role` を取得し、手動実行と同じ
ロール階層で参照可否を判定する。実行要求者本人だけに限定する条件は設けない。

## 判定ロジック

バッチ実行権限は以下の順で判定する。

| 順序 | 条件 | 結果 |
| --- | --- | --- |
| 1 | 未認証 | 拒否 |
| 2 | 認証ユーザーが `ADMIN` | 許可 |
| 3 | バッチ定義IDが未指定 | 拒否 |
| 4 | バッチ定義が存在しない | 拒否 |
| 5 | `required_role` が未設定、未知、`BAN_USER` | 拒否 |
| 6 | ユーザーのロールが `required_role` 以上 | 許可 |
| 7 | 上記以外 | 拒否 |

通常ロールの階層は `ADMIN > OPERATOR > USER` とする。
`required_role` は `ADMIN`、`USER`、`ROLE_ADMIN`、`ADMIN_ROLE` のような表記揺れを吸収して比較する。
未登録の将来ロールは文字列一致で許可せず、明示的に設計へ追加されるまで拒否する。

## 設計上の注意

| 項目 | 方針 |
| --- | --- |
| 防御の正本 | バックエンドの認可アノテーションと判定サービス |
| フロント制御 | UX向上のための表示・遷移制御であり、防御の正本にはしない |
| 権限式の分散 | Controller へ直接 `@PreAuthorize` を増やさず、専用アノテーションに寄せる |
| バッチ個別権限 | `m_batch_definitions.required_role` を利用する |

## 拡張方針

将来的に操作単位の権限が必要になった場合は、次のようなアノテーションを追加する。

```java
@CanOperateDevice
@CanReadStock
@CanManageLearning
```

リソース単位のACLが必要になった場合は、`resource_type`、`resource_id`、`action`、`role` または `user_id` を保持する権限テーブルを追加し、専用の判定サービスから参照する。

## 関連ドキュメント

- [認可設計](./0002_authorization.md)
- [バッチ管理 基本設計](../02_SystemDesign/バッチ管理/0006_batch_management_basic_design.md)
- [フロントエンド権限制御設計](../06_UserInterfaceDesign/3005_frontend_authorization.md)
