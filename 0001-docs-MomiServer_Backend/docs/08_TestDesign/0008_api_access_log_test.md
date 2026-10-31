# APIアクセスログテスト

## 対象

| No | 対象 | 観点 |
| --- | --- | --- |
| 1 | APIアクセスログフィルター | 記録項目、リクエストID、機密情報非保存、保存失敗時の応答維持 |
| 2 | APIアクセスログService | 検索条件正規化、期間制限、ページング、保持期間 |
| 3 | GET `/api/logs/access` | 実DB検索、ADMIN・OPERATOR許可、USER拒否 |
| 4 | GET `/api/logs/access/{requestId}` | 実DB詳細取得、存在しないIDの404 |
| 5 | `/logs/api` | 検索、一覧、4xx・5xx強調、詳細モーダル、ロール別導線 |

## 確認項目

- OPTIONSおよび`/api/**`以外を記録しない。
- レスポンスへ`X-Request-ID`を返し、同じUUIDでDBから参照できる。
- リクエスト・レスポンス本文、Authorization、Cookie、APIキー、クエリ文字列を保存しない。
- 共通エラーコード、HTTPステータス、処理時間、操作主体、発生元を記録する。
- ログ保存に失敗しても、本来のHTTPステータスを維持する。
- 検索期間を31日以内に制限し、1ページ50件、最大200件とする。
- ADMIN・OPERATORは一覧・詳細を取得でき、USERは403となる。
- 画面上部に取得エラーを表示し、再読込できる。

## テストケース

| No | テスト内容 | 前提条件 | 期待結果 |
| --- | --- | --- | --- |
| 1 | 正常APIの記録 | 認証済みOPERATOR | 200、web、メール、パス、処理時間を保存 |
| 2 | エラーAPIの記録 | 共通エラー応答 | 4xxまたは5xxとエラーコードを保存 |
| 3 | 機密情報非保存 | Authorizationとパスワード本文を送信 | 登録DTO・DB項目に機密値を含まない |
| 4 | 保存失敗 | Repository例外 | 元のAPIレスポンスを変更しない |
| 5 | 一覧権限 | ADMIN・OPERATOR・USER | ADMIN・OPERATORは200、USERは403 |
| 6 | 詳細取得 | 保存済みリクエストID | 保存内容を返す |
| 7 | 検索画面 | OPERATOR | 条件をAPIへ渡し、5xxを強調して表示 |
| 8 | 詳細モーダル | 一覧の詳細ボタン | 詳細APIを呼び、全保存項目を表示 |
| 9 | USERの画面遷移 | USER | サイドバー非表示、直接遷移は`/index`へ戻る |

## 確認結果

確認日: 2026/10/12。

| 確認項目 | 結果 | 補足 |
| --- | --- | --- |
| V32マイグレーション | 成功 | PostgreSQL 17へ適用 |
| Backend単体テスト | 成功 | フィルター・Service |
| Backend実DB統合 | 成功 | 保存、ロール認可、検索、詳細 |
| Frontend build・lint | 成功 | TypeScript、Vite、ESLint |
| Playwright | 成功 | APIログ画面1件と認可回帰5件 |

## 備考

- `docs/00_Templates/090_test_design.md`を基に作成した。
- WindowsのGradle NIO制約を避けるため、BackendはPodman JDK 25で実行した。
