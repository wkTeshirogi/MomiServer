# 週次ドキュメント更新メモ 2026/10/02

## サマリ

9月24日の文書更新と9月25日の進捗メモ以降の作業ツリーを確認した。
スケジュール実行状況・完了ログの実装を、要件からAPI・UIまで反映した。
未コミット変更を含むため、変更日と確定版は断定しない。
本メモは130_meeting_minutes、API設計は070_api_designの既存テンプレートに基づく。

## 差分一覧

| 区分 | 実装と文書の差分 | 対応 |
| --- | --- | --- |
| 実行状況 | 管理者向け期間集計APIが追加済みだが設計は追加方針のみ | API-0043と要件・基本・詳細設計へ反映 |
| 完了ログ | success・failed・timeout限定のログAPIとモーダルを追加 | API-0044とUIへ反映。全履歴表示とは分離 |
| DB | V26にカレンダー集計用部分インデックスがある | 対象テーブル定義へ追記 |
| 認可等 | 通知・在庫・フロント認証関連に変更あり | 今回の集計機能とは分け、横断確認を次回課題へ記録 |

## 決定事項

| No | 内容 |
| --- | --- |
| 1 | 実装済みの状況集計・完了ログと、従来の全履歴・スナップショット表示計画を区別する |
| 2 | 日付の絞り込みは予定日時基準と明示し、開始・終了日時基準と混同しない |
| 3 | API2件をAPI索引、本メモを議事録索引へ追加。自動ナビゲーションのmkdocs.ymlは変更しない |

## 作業内容

| 区分 | 確認したソースコード範囲 |
| --- | --- |
| バックエンド | BatchScheduleMasterController、BatchScheduleService、BatchScheduleExecutionRepository、集計・ログDTO、V26、GlobalExceptionHandler |
| テスト | BatchScheduleExecutionControllerTestの管理者取得・一般ユーザー拒否の定義を静的確認 |
| フロントエンド | Schedule/Listのservice・page、BatchScheduleExecutionLogModal、変更ファイル一覧 |
| 文書 | 9月24・25日のメモ、既存要件・基本・詳細・UI・DB・API索引、テンプレート、CI |
| Git | docsでfetch後、HEADとorigin/mainの差分0を確認。既存変更を保持しweekly-docs-20261002を作成 |

| 種別 | ファイル | 内容 |
| --- | --- | --- |
| 更新 | [バッチ要件](../01_RequirementsDefinition/Batch/0001_batch_management_requirements.md) | 状況集計・完了ログ参照 |
| 更新 | [バッチ基本設計](../02_SystemDesign/バッチ管理/0006_batch_management_basic_design.md) | 参照処理の分離 |
| 更新 | [スケジュール詳細](../02_SystemDesign/バッチ管理/Details/0002_batch_schedule_detail_design.md) | 実装範囲と検索処理 |
| 更新 | [実行要求テーブル](../04_DatabaseDesign/Tables/t_batch_execution_requests.md) | V26インデックス |
| 新規 | [API-0043](../05_API_Design/API/0043_batch_schedule_executions.md) | 状況集計API |
| 新規 | [API-0044](../05_API_Design/API/0044_batch_schedule_execution_logs.md) | 完了ログAPI |
| 更新 | [API索引](../05_API_Design/000_index.md) | 新規APIへの参照 |
| 更新 | [スケジュール画面](../06_UserInterfaceDesign/3002_batch_schedule.md) | ログモーダルの表示・操作 |
| 新規 | `weekly_document_progress_20261002.md` | 差分・検証・残課題 |
| 更新 | [議事録索引](000_index.md) | 本メモへの参照 |

## 進行中の作業

| 区分 | 内容 | 状態 |
| --- | --- | --- |
| 全履歴表示 | 実行中・結果未確認・パラメータ・添付一覧を含む参照 | 今回の完了ログAPIでは未対応 |
| 認可 | 通知・在庫・AuthContext・PrivateRoute等の変更の横断照合 | 差分が広く、今回は確定仕様として反映しない |
| 前回持越し | 添付・共通設定12項目の契約、旧転送API、Gateway旧構想 | 引き続き確認対象 |

## 課題・ブロッカー

| 区分 | 内容 | 影響 |
| --- | --- | --- |
| 日時 | DB予定日時とオフセットなしの応答を使用 | 運用タイムゾーンとブラウザ日付の契約は次回確認 |
| ログ | Repositoryは保存済みmessage・stdout・stderrを返す | 出力の機密情報処理は別途横断確認が必要 |
| 既存文書lint | 前回の既存4ファイル9件を今回再検証する | 今回変更によるエラーと分離する |

## 次回アクション

| 優先度 | 作業 | 目的 |
| --- | --- | --- |
| 高 | 通知・在庫・フロント認証の権限表とテスト対応を確認 | 設計との差分を確定 |
| 高 | 添付・共通設定・旧転送APIの契約照合を継続 | 保留仕様を解消 |
| 中 | 全履歴表示の未対応項目、日時・ログの扱いを確認 | 現行ログ機能との境界を維持 |

## 確認結果

| 確認項目 | 結果 | 補足 |
| --- | --- | --- |
| CI validate stage | 確認済み | markdownlint JSON・通常実行、MR時のみコメント投稿 |
| markdownlint JSON・通常実行 | 既存エラー9件 | 前回と同じ未編集4ファイル。全体validateは未成功 |
| 今回変更10ファイルのmarkdownlint | 成功 | 今回の更新に起因するエラーなし |
| mkdocs build --strict | 成功 | 既存siteを保持して別の一時出力を使用。終了コード0 |
| アプリlint・build・test | 未実施 | 文書のみ変更。ソースとテスト定義の静的確認を実施 |
| MRコメント投稿 | 対象外 | ローカル検証のため外部投稿しない |

## 備考

既存lintエラーは以下のとおり。今回の変更と無関係なため修正せず記録する。

| ファイル | エラー |
| --- | --- |
| `docs/04_DatabaseDesign/Tables/t_agent_mqtt_credentials.md` | 33行目MD047 |
| `docs/05_API_Design/API/0042_agent_runtime.md` | 46行目MD047 |
| `docs/07_OperationsDesign/0004_agent_batch_runtime.md` | 24・31・43・45・86行目MD013 |
| `docs/90_MeetingMinutesAndNotes/agent_auth_implementation_20260923.md` | 54・74行目MD013 |

ソース実装と既存のステージ済み変更は保持する。commit・pushは実施しない。
