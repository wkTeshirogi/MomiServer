# 週次ドキュメント更新メモ 2026/08/20

## 対象期間

2026/08/14 以降の差分を中心に確認した。

## 確認したソースコード範囲

| 区分 | 確認範囲 |
| --- | --- |
| フロントエンド | `src/App.tsx`、`src/pages/Master/MasterList`、`src/pages/Master/Items` |
| バックエンド | `item/controller`、`item/service`、`item/repository`、`item/dto` |
| バックエンド | `batch/controller`、Gateway APIキー認証設定、`application.yaml` |
| DB | Flyway `V12__create_m_inventory_items.sql` |
| ドキュメント | API設計、DB設計、UI設計、テンプレート、週次メモ |

## 作成したドキュメント

| ドキュメント | 内容 |
| --- | --- |
| `docs/05_API_Design/API/0028_master_item_list.md` | 在庫品目一覧取得 API |
| `docs/05_API_Design/API/0029_master_item_detail.md` | 在庫品目詳細取得 API |
| `docs/05_API_Design/API/0030_master_item_register.md` | 在庫品目登録・更新 API |
| `docs/06_UserInterfaceDesign/3003_item_master.md` | 在庫品目マスタ画面設計 |
| `docs/90_MeetingMinutesAndNotes/weekly_document_progress_20260820.md` | 今回の週次ドキュメント更新メモ |

## 更新したドキュメント

| ドキュメント | 更新内容 |
| --- | --- |
| `docs/05_API_Design/000_index.md` | 在庫品目マスタ API を追加し、Agent API を `/api/v1/agent` 系へ更新 |
| `docs/05_API_Design/API/0023_agent_batch_execution_start.md` | Agent API のパスと Gateway APIキー認証前提を更新 |
| `docs/05_API_Design/API/0024_agent_batch_execution_complete.md` | Agent API のパスと Gateway APIキー認証前提を更新 |
| `docs/05_API_Design/API/0026_agent_batch_execution_file_list.md` | Agent API のパスと Gateway APIキー認証前提を更新 |
| `docs/05_API_Design/API/0027_agent_batch_execution_file_download.md` | Agent API のパスと Gateway APIキー認証前提を更新 |
| `docs/02_SystemDesign/バッチ管理/0003_batch_agent_requirements.md` | Agent API のパスを `/api/v1/agent` 系へ更新 |
| `docs/02_SystemDesign/バッチ管理/0004_batch_agent_basic_design.md` | Agent API のパスを `/api/v1/agent` 系へ更新 |
| `docs/02_SystemDesign/バッチ管理/0005_batch_agent_detailed_design.md` | Agent API のパスを `/api/v1/agent` 系へ更新 |
| `docs/02_SystemDesign/バッチ管理/Details/0004_batch_agent_integration_detail_design.md` | Agent API のパスを `/api/v1/agent` 系へ更新 |
| `docs/06_UserInterfaceDesign/0000_index.md` | 在庫品目マスタ画面を追加 |
| `docs/04_DatabaseDesign/Tables/m_inventory_items.md` | 実DBのステータス値を `active`、`disabled`、`deleted` として明記 |
| `docs/00_Templates/020_functional_requirement.md` | markdownlint の重複見出し指摘を避けるため、テンプレート見出し名を最小修正 |
| `mkdocs.yml` | 未掲載ページを含めて表示するため、固定 nav を解除し MkDocs の自動ナビゲーションへ変更 |

## 実装と既存ドキュメントの主な差分

| 項目 | 実装で確認した内容 | ドキュメント対応 |
| --- | --- | --- |
| 在庫品目マスタ API | `/api/master/item/list`、`/detail`、`/register` が追加済み | API設計書を3件追加 |
| 在庫品目マスタ画面 | `/master/items`、`/master/items/:itemId` が追加済み | UI設計書とUI目次へ追加 |
| 在庫品目ステータス | DB制約値は `active`、`disabled`、`deleted` | DB設計の表示値を実装値へ補正 |
| Agent API パス | `/api/v1/agent/batch/executions/**` へ変更済み | API設計書、API目次、バッチ管理設計を更新 |
| Gateway APIキー | `/api/v*/agent/**`、`/api/v*/iot/**` で `X-API-Key` を検証 | Agent API の認証欄へ反映 |

## 確認事項

| 項目 | 内容 | 理由 |
| --- | --- | --- |
| 在庫数量管理 | 実在庫、在庫変動履歴の Controller / Service は今回の確認範囲では未確認 | テーブル設計はあるが、今回確認した実装は品目マスタ中心のため |
| 在庫品目詳細APIの未存在時ステータス | サービス層は `IllegalArgumentException` を送出する | 共通例外ハンドリングでの最終HTTPステータス変換を別途確認する必要がある |
| Gateway APIキーの運用値 | デフォルト設定では APIキー認証が有効だが、キー一覧は環境変数依存 | 実運用環境のキー配布、無効化、ローテーション方針は実装だけでは確定できない |

## ナビゲーション更新

未掲載ページを含めて表示するため、`mkdocs.yml` の固定 nav を解除し、MkDocs の自動ナビゲーションへ変更した。
これにより、週次で追加される設計書やテンプレートが nav 未掲載のまま残ることを防ぐ。

## 実行した検証内容と結果

| 検証 | 結果 | 補足 |
| --- | --- | --- |
| ドキュメントリポジトリの Git 状態確認 | 完了 | 既存の未追跡 `site/` は今回作業では未変更 |
| ソースコードリポジトリの Git 状態確認 | 完了 | バックエンドに既存の staged 変更があるため、上書きしない方針で確認 |
| `git fetch` | 完了 | 初回は名前解決・権限制約で失敗し、権限承認後に成功 |
| markdownlint JSON 出力 | 成功 | `npx markdownlint-cli docs/**/*.md --json > lint-results.json` を実行 |
| markdownlint 本検証 | 成功 | `npx markdownlint-cli docs/**/*.md` を実行 |
| MkDocs build | 成功 | `mkdocs build --strict` を一時出力先で実行。nav未掲載ページの情報ログは継続 |

## 残っている課題、次回確認すべき事項

| 優先度 | 内容 |
| --- | --- |
| 高 | 実在庫管理 API、在庫変動履歴 API、対応画面の実装有無を確認する |
| 中 | APIキー発行、無効化、スコープ、ローテーションの詳細設計を確認する |
| 中 | Agent API の APIキー運用、リトライ、署名検証の必要性を確認する |
| 低 | `mkdocs.yml` の nav 整理方針を決め、未掲載ページをまとめて反映する |
