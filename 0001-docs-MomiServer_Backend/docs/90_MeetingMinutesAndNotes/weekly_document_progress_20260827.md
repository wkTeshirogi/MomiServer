# 週次ドキュメント更新メモ 2026/08/27

## 対象期間

2026/08/21 以降の差分を中心に確認した。

## 確認したソースコード範囲

| 区分 | 確認範囲 |
| --- | --- |
| フロントエンド | `src/App.tsx`、`src/components/layout/Sidebar.tsx`、`src/pages/Stock` |
| フロントエンド | `src/pages/Master/Batch/Schedule`、`src/lib/cronSchedule.ts` |
| バックエンド | `item/controller`、`item/service`、`item/repository`、`item/dto` |
| バックエンド | Flyway `V13`、`V14`、`V15` |
| バックエンド | `ItemStockServiceTest` |
| ドキュメント | API設計、DB設計、UI設計、週次メモ、MkDocs設定 |

## 作成したドキュメント

| ドキュメント | 内容 |
| --- | --- |
| `docs/05_API_Design/API/0035_stock_list.md` | 実在庫一覧取得 API |
| `docs/05_API_Design/API/0036_stock_detail.md` | 実在庫詳細取得 API |
| `docs/05_API_Design/API/0037_stock_save.md` | 実在庫登録・更新 API |
| `docs/05_API_Design/API/0038_stock_operation.md` | 実在庫数量操作 API |
| `docs/05_API_Design/API/0039_stock_history.md` | 実在庫履歴取得 API |
| `docs/06_UserInterfaceDesign/3004_stock.md` | 在庫管理画面設計 |
| `docs/90_MeetingMinutesAndNotes/weekly_document_progress_20260827.md` | 今回の週次ドキュメント更新メモ |

## 更新したドキュメント

| ドキュメント | 更新内容 |
| --- | --- |
| `docs/04_DatabaseDesign/Tables/t_inventory_stocks.md` | 実DDLに合わせてカラム、ステータス値、最低数量参照元を補正 |
| `docs/04_DatabaseDesign/Tables/t_inventory_stock_histories.md` | 実DDLに合わせて変更種別の値を補正 |
| `docs/05_API_Design/000_index.md` | 実在庫 API 5件を追加 |
| `docs/06_UserInterfaceDesign/0000_index.md` | 在庫管理画面を追加 |
| `docs/90_MeetingMinutesAndNotes/000_index.md` | 2026/08/27 の週次メモを追加 |

## 実装と既存ドキュメントの主な差分

| 項目 | 実装で確認した内容 | ドキュメント対応 |
| --- | --- | --- |
| 実在庫 API | `/api/stock/list`、`/detail`、`/save`、`/operation`、`/history` が追加済み | API設計書を5件追加 |
| 在庫管理画面 | `/stock`、`/stock/:itemId`、互換ルート `/Stock/...` が追加済み | UI設計書とUI目次へ追加 |
| 実在庫テーブル | `minimum_quantity` は存在せず、`replenish_quantity` を保持 | DB設計を実DDLに合わせて補正 |
| 在庫ステータス | DB値は `available`、`low`、`out_of_stock`、`deleted` | DB設計とAPI設計へ実装値を明記 |
| 変更種別 | DB値は `inbound`、`outbound`、`stocktake`、`discard`、`delete` | 履歴テーブル設計と操作API設計へ反映 |
| 数量操作 | 操作時に行ロックし、品目マスタの `min_quantity` でステータスを算出 | 操作API設計へ反映 |

## 確認事項

| 項目 | 内容 | 理由 |
| --- | --- | --- |
| 数量操作APIの画面利用 | 現行フロントエンドでは `/api/stock/operation` と `/api/stock/history` を直接利用していない | 画面で履歴を表示するか、保存APIで数量変更を許容するか運用判断が必要 |
| 実在庫APIの権限制御 | Controller側に ADMIN 制約はなく、認証済みユーザーが利用可能 | 在庫操作を管理者限定にするかは実装だけでは判断できない |
| 例外時HTTPステータス | サービス層は `IllegalArgumentException` を送出する | 共通例外ハンドリングでの最終HTTPステータス変換を別途確認する必要がある |
| バッチスケジュールID補正 | `V13__ensure_batch_schedule_id_default.sql` で ID sequence と default を補正 | 既存DB設計のカラム定義自体には大きな変更がないため、今回は週次メモへの記録に留めた |

## ナビゲーション更新

`mkdocs.yml` は今回の週次ドキュメント追加では変更していない。
既存状態として固定 nav は解除されており、MkDocs の自動ナビゲーションで未掲載ページを含めて表示される。

## 実行した検証内容と結果

| 検証 | 結果 | 補足 |
| --- | --- | --- |
| ドキュメントリポジトリの Git 状態確認 | 完了 | 既存の staged `mkdocs.yml` と未追跡 `site/` を確認 |
| ソースコードリポジトリの Git 状態確認 | 完了 | ソース側は未コミット変更を読み取り対象とし、変更は加えていない |
| `git fetch` | 完了 | 初回は名前解決・権限制約で失敗し、権限承認後に成功 |
| markdownlint JSON 出力 | 成功 | `npx markdownlint-cli docs/**/*.md --json > lint-results.json` を実行 |
| markdownlint 本検証 | 成功 | `npx markdownlint-cli docs/**/*.md` を実行 |
| MkDocs build | 成功 | `$env:TEMP` 出力は権限エラー。ワークスペース内の一時出力先で `mkdocs build --strict` 成功 |

## 残っている課題、次回確認すべき事項

| 優先度 | 内容 |
| --- | --- |
| 高 | 数量操作APIと履歴取得APIを画面へ組み込むか判断する |
| 高 | 在庫管理APIを管理者限定にするか、認証済みユーザー全体に許可するか確認する |
| 中 | 実在庫APIの例外ハンドリングとHTTPステータスを確認する |
| 中 | バッチスケジュールID sequence補正をDB運用設計に追記するか確認する |
