# 月次ドキュメント作業まとめ 2026/09

## 月次サマリ

2026年9月3日、10日、17日、24日の週次ドキュメント更新メモを集約した。
Gatewayレート制限、CORS、通知、バッチ運用、共通設定、Agent認証、添付配布を中心に、実装と既存設計の差分を段階的に整理した。
月後半には静的な差分確認に加え、CI・DBテストの前提や運用設定を文書化し、未検証事項を明示する運用が定着した。

## 主な作成・更新領域

| 領域 | 月内の主な反映内容 |
| --- | --- |
| Gateway | APIキー認証、レート制限、複数インスタンス時の制約、関連テストを整理 |
| セキュリティ | CORS外部化、SecurityConfig、JWT・Gateway・Controllerのテスト範囲を確認 |
| 通知 | 通知保存・配信・ルーティング、画面表示・既読管理へつながる設計を更新 |
| バッチ・運用 | 起動設定、環境変数、CIのPostgreSQL・Flyway・jOOQ・テスト順序を反映 |
| Agent・添付 | 専用Bearer認証、登録・challenge・更新の分離、添付制限と保持設定を整理 |
| 共通設定 | 設定項目、整数上限、設定バージョン、保存中制御、再読込の画面要件を整理 |

## 主な設計判断

- Gatewayレート制限はAPIキー認証の有効・無効に応じて識別単位を分ける。
- CORSの許可元は環境依存設定として外部化し、コードへ固定しない。
- Agent認証は一般ユーザーJWTや旧APIキーと分離し、専用Bearerと端末世代認可を前提とする。
- CI定義、ローカル検証、実運用環境の成功を混同せず、それぞれの確認範囲を明記する。
- 設計と実装の差が大きい機能は、局所的な確認だけで既存設計全体を置換しない。

## 実装・ドキュメント整合

9月前半はGateway、CORS、通知、メッセージカタログおよび関連自動テストを確認し、Phase 1成果物とAPI設計へ反映した。
中盤はバッチ起動設定、共通設定UI、DBテストのCI実行前提を整理した。
後半はAgent認証、MQTT実行、添付配布の実装記録と照合し、固定Webhook除去や設定項目の扱いを文書へ反映した。

## 継続課題・要確認

- 複数インスタンス構成でGatewayレート制限を共有する方式。
- Agent認証について、API、フィルタ、SecurityConfig、テストを横断した最終整合。
- 添付制限・共通設定12項目のUI、DB、API、運用を含む全体照合。
- OpenAPI生成コードと追加された自動テストの設計書への分類。
- 本番CORS、Podman、旧Webhook失効、OSサービス起動の実環境確認。
- リポジトリ全体の既存Markdown lint違反とアプリ側静的解析課題の整理。

## 確認結果まとめ

9月3日・10日・17日は全Markdownのlint成功、4件すべてでMkDocs strictビルド成功が記録されている。
9月24日は変更対象ファイルのlintに成功した一方、リポジトリ全体のvalidateには既存違反が残ると記録されている。
アプリケーションの実行テストや本番環境確認を実施していない週があるため、静的照合結果と動作保証は分けて扱う。

## 集約対象

- `weekly_document_progress_20260903.md`
- `weekly_document_progress_20260910.md`
- `weekly_document_progress_20260917.md`
- `weekly_document_progress_20260924.md`

2026/10/31に上記4件を再照合した。従来の週次メモ保持方針を月次集約後の削除方針へ変更する。
以下は各作業日当時の記録であり、10月末時点の実装・課題の再判定ではない。
元の週次メモはGit履歴から復元できることを確認したうえで削除する。

## 2026/09/03

### 09/03 確認範囲と比較基準

8月28日以降を中心に確認。コミットIDの記録はない。
バックエンドはGateway認証フィルタ・レート制限・設定、Agent連携API、application.yaml、GatewayRateLimiterTest。
フロントエンドは認可ガード、ロール判定、メッセージカタログ、在庫管理画面の差分を対象とした。

### 09/03 変更文書と判断

- `00_ProjectOverview/010_phase1_deliverables.md`: レート制限・テストを実装済みへ更新。
- `02_SystemDesign/API Gateway/0001_gateway_basic_design.md`: 固定ウィンドウ方式の分間制限を反映。
- `05_API_Design/000_index.md`: Agent連携の認証方式を更新。
- `05_API_Design/API/0023_agent_batch_execution_start.md`、`0024_agent_batch_execution_complete.md`: 認証・制限を反映。
- `05_API_Design/API/0026_agent_batch_execution_file_list.md`、`0027_agent_batch_execution_file_download.md`: 認証を更新。
- `05_API_Design/API/0031_agent_file_upload.md`、`0032_agent_file_download.md`: 認証とGateway系例外を反映。
- `05_API_Design/API/0033_agent_file_download_complete.md`、`0034_agent_file_download_failed.md`: 認証を更新。

当時はAPIキー認証有効時にX-API-Keyを検証し、Agent・IoT経路へレート制限を適用していた。
429、GATEWAY_RATE_LIMIT_EXCEEDED、X-RateLimit-Limit、X-RateLimit-Remaining、Retry-Afterを設計へ反映。
画面・在庫APIは適用対象外で、状態はプロセス内保持のため複数インスタンス間では共有しない。
8月以前の「制限は設定のみ」「AgentにAPIキーを使わない」という記録は当時の履歴として扱った。
Agent認証のその後の変更は9月24日の記録を参照する。

### 持越し事項

キー発行・無効化・ローテーション・スコープ管理、APIバージョン管理API、Gateway管理画面、APIログ表示APIは未確認または未実装。
レート制限共有方式とAgent連携API全体の統合テスト拡充を継続課題とした。

## 2026/09/10

### 09/10 確認範囲と比較基準

9月4日以降を中心に確認。コミットIDの記録はない。
バックエンドはCORS、SecurityConfig、application.yaml、Podman補助ファイル、systemd用envサンプル、自動テスト追加状況。
フロントエンドは認可ガード、在庫管理画面、メッセージカタログ、OpenAPI生成コード差分を対象とした。

### 09/10 変更文書と判断

- `07_OperationsDesign/0002_configuration_management.md`: CORS環境変数、Podman Compose・Quadlet運用補助を追記。
- `11_SecurityDesign/0003_cors.md`: 固定許可元からapp.cors.allowed-origins / APP_CORS_ALLOWED_ORIGINS管理へ更新。
- `90_MeetingMinutesAndNotes/monthly_progress_202608.md`: MD013対応の改行のみ。

Gateway、JWT、SecurityConfig、Controller API契約、メッセージカタログ、通知、バッチ権限の追加テストを確認した。
生成コードの差分範囲が広く、API設計の全面更新とテスト設計の再分類は保留した。
本番ドメインに合わせたCORS、Linuxのbridge・SELinux・rootless UID/GID確認を持越しとした。

## 2026/09/17

### 09/17 確認範囲と比較基準

9月10日以降を基準とし、バックエンドHEAD f6d38dbの直近8コミットと設定・CI・SecurityConfig・共通設定Controller・保存DTOを確認。
フロントエンドHEAD 835636fのApp.tsx、CommonSettings/Batchのpage・service、package.jsonを確認。
文書HEADは7f5588a。fetch後のorigin/mainとの差分は0で、既存変更を保持して作業ブランチを作成した。

### 09/17 変更文書と判断

- `07_OperationsDesign/0001_start_stop.md`: バッチ自動起動enabled=trueとコメント・設計の矛盾を分離。
- `07_OperationsDesign/0002_configuration_management.md`: Discord通知・監査通知の既定trueと固定Webhookの存在を記録。
- `08_TestDesign/0004_database_test.md`: PostgreSQL準備、Flyway、jOOQ生成、テストのCI実行順を反映。

設定の実装値と推奨・運用意図を区別し、Webhook秘密値は転載しなかった。
固定値除去・失効・再発行と対象Jobの意図確認を優先課題とした。
Agent認証はSecurityConfigのauthenticated指定とGatewayフィルタを横断確認するまで設計全体を置換しない方針とした。

### 共通設定の確認と持越し

画面は `/master/common-settings/batch`、GET・POSTは `/api/master/common-settings/batch` でAdminOnly。
当時は実行制御4項目・結果送信確認4項目の計8項目が必須。再送回数は0以上、他は1以上。
結果待ち期限は標準タイムアウトより長く、UIの整数上限は2147483647。
設定バージョン表示、保存中無効化、ヘッダー直下通知、取得失敗時再読込を静的確認したが動作試験は未実施。
要件・基本・詳細設計からDB・API・UIへ展開する作業と、前週のOpenAPI・追加テスト分類・本番確認を持越した。

## 2026/09/24

### 09/24 確認範囲と比較基準

9月17日以降の作業ツリーが基準。未コミット変更が多く、コミット履歴だけで期間を厳密に特定できない。
GatewayApiKeyAuthFilter、AgentAuthFilter、AgentBatchExecutionController、BatchExecutionFileController、経路定義を確認。
フロントエンドはNotificationBanner、Batch共通設定service、api/client.ts、変更一覧を確認した。
文書fetch後のHEADとorigin/mainの差分は0。既存変更を保持して作業ブランチを作成した。

### 09/24 変更文書と判断

- `01_RequirementsDefinition/FR/0067.md`: Agent専用BearerとIoT APIキーを分離。
- `02_SystemDesign/API Gateway/0001_gateway_basic_design.md`: 現行境界を冒頭へ明記し、旧構想の全面整理は保留。
- `05_API_Design/API/0023_agent_batch_execution_start.md`: 開始通知の認証注意事項を修正。
- `05_API_Design/API/0026_agent_batch_execution_file_list.md`: Bearer・端末・世代認可を反映。
- `05_API_Design/API/0027_agent_batch_execution_file_download.md`: Bearer認証へ更新。
- `09_DevelopmentGuidelines/0005_message_catalog.md`: 通知の閉じる操作・再表示・スクロール仕様を追記。

要件、基本設計、API詳細の順に反映し、専用認証設計への参照で重複を抑えた。
Agentの登録・challenge・更新の分離を確認したが実行試験は未実施。
共通設定は添付単体MiB・合計MiB・件数・作業ディレクトリ保持日数を含む12項目へ拡大しており、全体照合は未完了。
手書きクライアントはVITE_API_BASE_URLの末尾スラッシュを除去し/apiを付加する。全クライアント間の整合は未確認。

### 持越し事項と経緯

旧Gatewayのユーザー別制限・DBバージョン管理は構想と実装が混在していた。
API-0031～0034の経路は確認したController群では見つからず、提案・未実装の分類を要する。
V23～V25、添付専用APIと既存実行ファイルAPIの関係、端末・世代・上限・保存先、UI・DB・APIの契約照合を持越した。
固定Webhook除去と起動設定には既存文書で対応記録があるが、旧Webhook失効と本番動作は未確認。

## 文書一覧の共通事項

上記パスはdocs配下を基準とする。各週に当該週次メモを新規作成し、議事録・メモの000_index.mdへ追加した。
各週ともmkdocs.ymlは変更していない。月後半は自動ナビゲーション維持を明記した。
既存ステージ済み変更・ソース変更・ログ・siteは保持し、commit・pushは行っていない。

## 日付別の検証履歴

| 実施日 | Markdown検証 | MkDocs strict | 未実施・制約 |
| --- | --- | --- | --- |
| 09/03 | 全体JSON・通常とも成功 | 一時出力先で成功 | アプリ試験実行結果の記録なし |
| 09/10 | 全体JSON・通常とも成功 | 一時出力先で成功 | 文書変更のみのためアプリlint/build/test未実施、MR投稿対象外 |
| 09/17 | 全体JSON・通常とも成功、既存CLI使用 | 一時出力先で成功、終了0 | アプリlint/build/test未実施、MR投稿対象外、実運用・CI成功未確認 |
| 09/24 | 全体は既存4ファイル9件のエラー、変更8ファイルは成功 | 一時出力先で成功、終了0 | アプリlint/build/test未実施、MR投稿対象外 |

全週でvalidateがmarkdownlintのJSON・通常実行であることを確認した。以下は9月24日時点の未修正エラー記録である。

| ファイル（docs配下） | 当時の指摘 |
| --- | --- |
| 04_DatabaseDesign/Tables/t_agent_mqtt_credentials.md | 33行 MD047 |
| 05_API_Design/API/0042_agent_runtime.md | 46行 MD047 |
| 07_OperationsDesign/0004_agent_batch_runtime.md | 24・31・43・45・86行 MD013 |
| 90_MeetingMinutesAndNotes/agent_auth_implementation_20260923.md | 54・74行 MD013 |

## 集約処理の記録

- 集約日: 2026/10/31（日本時間）。前月である9月分の残存メモを再処理。
- 対象: 集約対象欄の4件。10月・8月以前・別形式のメモと既存のmonthly_progress_202609.mdは変更しない。
- 参照更新: 索引とproject_task_listの該当リンクのみ月次メモの日付見出しへ変更する。
- 削除状態: 集約対象欄の4件を削除。全件で未コミット変更なし・HEADから復元可能・対象配下の9月分であることを確認。
- 削除保留: なし。
- 削除前検証: 全Markdownのlint成功、MkDocs strict成功。初回の重複見出し6件は修正済み。
- 削除後検証: 全MarkdownのJSON形式lintとMkDocs strictは終了コード0。削除対象へのリンク残存なし、4日分のHTMLアンカーを確認。
- 最終確認: 記録追記後に通常形式lintとGit差分の空白検査も実施する。MR投稿はローカル検証の対象外とした。
- 既存変更: 索引の10/29追加とproject_task_listの作業状況変更を保持し、9月への参照のみ修正。mkdocs.ymlは未変更。
