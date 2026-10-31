# 外部APIキー・在庫連携 実装・文書更新記録 2026/10/20

## 概要

Phase 1の外部APIを在庫参照・入庫・出庫・棚卸しに限定し、ADMIN向けキー管理を実装した。
10月16日時点の未実装記録は履歴として保持し、今回の実装状態は本記録を参照する。
`140_change_log.md`テンプレートを使用した。

## 運用方針

- Web JWT、Agent専用認証、既存IoT固定キーと専用外部キーを分離する。
- 通信はHTTPS、キーはヘッダー送信とする。公開環境で平文待受を直接公開しない。
- 原文は発行時のみ返却し、無効化済みキーの再有効化は提供しない。
- 将来のナンバー認識・車庫在車確認は別スコープ・別APIで拡張する。今回の対象には含めない。

## 変更一覧

| 日付 | バージョン | 区分 | 変更内容 | 関連ドキュメント |
| --- | --- | --- | --- | --- |
| 2026/10/20 | V37 | 変更 | 旧仕様のユーザー本人管理をADMIN管理へ変更し、在庫スコープに限定 | [設計](../02_SystemDesign/API%20Gateway/0002_external_api_keys.md) |
| 2026/10/20 | V37 | 追加 | 発行・無効化、スコープ認可、DB共有レート制限、外部在庫API | [API契約](../05_API_Design/API/0048_external_inventory.md) |
| 2026/10/20 | V37 | 追加 | キー情報、発行・無効化監査、操作IDの重複防止記録 | [DB定義目次](../04_DatabaseDesign/000_index.md) |
| 2026/10/20 | V37 | 追加 | ADMIN限定の管理画面、原文一度限り表示、上部通知、共通ページング | [画面設計](../06_UserInterfaceDesign/3009_external_api_keys.md) |

## 確認結果

| 確認項目 | 結果 |
| --- | --- |
| Flyway・jOOQ | 新規の専用PostgreSQLでV1～V37適用と生成が成功 |
| バックエンド全JUnit | 330件中失敗0・エラー0・スキップ2。DB障害時の認証拒否・キー原文のログ除外も確認 |
| 新規Java静的チェック | 外部連携の新規12ファイルを独立Checkstyleタスクで検証し、指摘0 |
| 最終関連試験 | 整形後の外部API・専用認証フィルター・アクセスログ26件を再実行し、失敗0 |
| 外部API実DB統合 | 18件成功。同時再送、入力相違、失効、スコープ、履歴・通知、失敗時ロールバックを確認 |
| 既存画面回帰 | Playwrightによるロール別ナビゲーション・共通ページング6件成功 |
| 新画面確認 | Playwright CLIで発行・原文破棄・無効化・通知位置・件数切替・ロール制御を確認 |
| レスポンシブ | 1440px・390pxでスクリーンショットと横はみ出しを確認 |
| フロントエンド | ESLint・本番build成功 |
| 文書 | Markdownlint成功 |

## 備考

- 既存DBには試験データ・マイグレーションを適用していない。利用環境ではV37適用後にバックエンドを起動する。
- スコープ変更は新キー発行と旧キー無効化で行う。未確定操作を新キーで再送すると重複防止範囲が異なる。
- 専用の自動ローテーション、監査履歴閲覧画面、キー別上限変更、重複防止記録の保持削除は対象外。
- 全体Checkstyleは既存コードとjOOQ生成コードの指摘で失敗する。新規実装範囲の指摘0とは分けて扱う。

## SonarQube CRITICAL指摘への対応

利用者提供の`search.json`に含まれるCRITICAL 19件を対象に、7ファイルの実装と照合して修正した。
添付は全539件のうち先頭100件であり、未収録ページの指摘は今回の確認範囲に含めない。

| ルール | 件数 | 修正内容 |
| --- | --- | --- |
| java:S1192 | 15 | ロール・状態・JSON/DB項目名を定数化し、MIME型はSpringの既存定数を使用 |
| java:S3776 | 1 | 車検証取込の内容判定と拡張子照合を分割。形式不一致・破損画像の拒否は維持 |
| java:S6809 | 3 | 車両登録・更新・管理終了からのTransactionalメソッド自己呼び出しを、非公開の取得処理へ変更 |

| 変更ファイル | 対応 |
| --- | --- |
| SecurityConfig.java | ADMINの重複を既存ロール定義へ集約 |
| VehicleImportService.java | MIME定数共用とファイル判定の分割 |
| VehicleRepository.java | ARCHIVEDの重複を既存車両状態定義へ集約 |
| VehicleService.java | リソース名の定数化とトランザクション自己呼び出し解消 |
| AgentRuntimeController.java | 応答項目server_timeの定数化 |
| AgentAuthService.java | 状態とDB項目名の定数化 |
| AgentMqttProvisioning.java | 暗号化コンテキストとDB・制御API項目名の定数化 |

車両の公開メソッドにあるトランザクション境界は維持し、数量・認証・APIレスポンス形式は変更していない。
特にMQTT資格情報の暗号化コンテキストとトピック、Agent応答の`server_time`を回帰試験で確認する。
SonarQubeの認証トークンが実行環境にないため、サーバー上での指摘解消確認は再解析後に行う。

専用PostgreSQLで全JUnitを実行し、348件中失敗0・エラー0・スキップ2だった。
追加18ケースはPDF・PNG・JPEGと不正入力14件、車両詳細2件、MQTT資格情報・無効設定2件で、すべて成功した。
Agent実DB統合試験では応答のserver_timeも確認した。外部MQTT実機試験の有効化条件は変更していない。

## 定数定義方針の適用

共通方針`08_定数定義方針.md`と関連するJava列挙型方針を確認し、直前のCRITICAL対応範囲へ適用した。
既存コード全体の定数移動は行わず、利用範囲に合った専用ファイルへ定義を整理する。

| 利用範囲 | 定義先 | 公開範囲・方針 |
| --- | --- | --- |
| プロジェクト共通のロール | common.constants.RoleEnum | 既存Enumを直接参照。SecurityConfig内の別名定数は削除 |
| 車両の管理状態 | vehicle.model.VehicleInspectionStatus | 既存Enumを直接参照。Repository内の別名定数は削除 |
| Agentの登録状態 | agent.model.AgentRegistrationStatus | 明示的なstoredNameを持つEnum。既存の小文字コードを維持 |
| Agent Controllerの応答項目 | agent.controller.AgentControllerConstants | クラス・フィールドともpackage-private |
| Agent ServiceのDB項目・基盤定数 | agent.service.AgentServiceConstants | クラス・フィールドともpackage-private。世代などの重複定義を集約 |
| 車両Serviceの識別子・業務日付 | vehicle.service.VehicleServiceConstants | クラス・フィールドともpackage-private |

Agent登録状態はDBで定義済みのpending・registered・disabledだけを表す。
未登録表示のunregisteredやMQTT同期状態とは意味が異なるため、共通の状態Enumへまとめない。
既存DTOの文字列表現、DB制約、暗号化コンテキスト、トピック、ロール認可は変更しない。
今後の追加・修正でも同じ配置判断を行えるよう、バックエンドのAGENTS.mdへ参照方針を追記した。

| 確認項目 | 結果 |
| --- | --- |
| Flyway・jOOQ・全JUnit | 専用PostgreSQLでV1～V37適用・生成が成功。353件中失敗0・エラー0・スキップ2 |
| Agent登録状態の追加試験 | 既存コード3種のJSON往復・保存値の復元、null、不明値拒否の5ケースが成功 |
| 新規4ファイルのCheckstyle | 専用定数クラス3ファイルとEnumを検証し、指摘0 |
| 既存環境への影響 | 試験専用DBを使用し、既存DBのデータ・設定は変更していない |

## 定数定義の全体確認

追加依頼により、直前のCRITICAL対応範囲だけでなくBE・FE・Agentの手書き実装へ確認範囲を広げた。
今回の対象は固定値、選択肢、表示ラベル、既定値、正規表現の定義と参照であり、業務仕様やDB・APIのコード値は変更しない。

| 対象 | 修正内容・主な定義先 |
| --- | --- |
| BE | 認証期限、暗号処理のサイズ、利用制限、ログ検索条件、通知、OCR、車検証解析などの固定値を同一パッケージの専用Constantsへ整理 |
| BEのパッケージ横断 | Agentの稼働判定はagent.AgentRuntimeConstants、外部API利用制限はexternal.ExternalApiConstantsへ配置。ServiceからFilterへの定数参照を解消 |
| BEの既存定義利用 | WebSocket認可のロールと外部在庫操作種別は既存Enumを参照。分のミリ秒換算はTimeUnitを利用し、重複定義を削除 |
| FE共通 | src/constantsのauth・api・inventory・paginationで認証保存キー、接続先、品目状態、20・50・100件と既定20件を管理 |
| FE機能内 | 対象階層の専用ファイルへ選択肢・表示定義・初期値を移動。通知設定、ユーザーロール、稼働状態、バッチ有効表示、Cron曜日、車両初期値の重複を集約 |
| Agent | internal/registration/constants.goへ版・通信期限・更新猶予・状態コードを配置。実行状態、ジャーナル段階、実測サービス状態は別の定数群として維持 |
| 開発規約 | BEのAGENTS.md、FE・AgentのREADMEへ配置方針と適用時の注意を反映 |

同じ値でも意味が違うものは統合しない。Agentのハートビート期限と実測値の鮮度、通知ロールと認証ロール、
Gatewayの対象パスとAPIバージョン検証の対象パスは、それぞれの契約を維持する。
Logger、SecureRandom、APIクライアント、React Contextなどの実行時オブジェクト、jOOQのクエリメタデータ、生成コード、外部依存、テストデータは機械的な移動対象から除外した。
未命名リテラルの全面置換や、既存Enum・DTOの全面設計変更は行っていない。

BEにはConstantDefinitionPolicyTestを追加し、対象の固定値が専用ファイルにあることと、定数クラスの継承・インスタンス生成を防ぐ構造を検証する。
Agentには既存の16コード表現と認証通信期限・更新猶予の互換性を確認するテストを追加した。

| 確認項目 | 最終結果 |
| --- | --- |
| BE Flyway・jOOQ | 専用PostgreSQLへのV1～V37適用・コード生成が成功。既存DBは未変更 |
| BE全JUnit | 355件中失敗0・エラー0・スキップ2。方針テスト2件も成功 |
| BE Checkstyle | 定数定義37ファイルと方針テスト1ファイル、計38ファイルで指摘0 |
| FE | ESLint・TypeScript検査・本番ビルド成功 |
| FE Playwright | 最終コードで43件すべて成功。ロール別表示、認証、通知、ページング、車両、WebSocketを模擬API・模擬接続で確認 |
| Agent Windows | go test・go vet成功、変更Goファイルのgofmt差分なし |
| Agent Linux | Go 1.25のCI相当コンテナでgo test -race成功 |
| Agentクロスビルド | Windows・Linux・macOSそれぞれamd64・arm64の6構成で成功 |

BEのスキップ2件は、明示有効化が必要な実Mosquitto連携と、Linuxでは該当しないWindows短縮一時パスの試験。
FEの模擬API試験は実バックエンドとの結合確認とは区別する。既存の全体Checkstyle指摘とSonarQube再解析は別途扱う。
既存のブランチと未コミット変更を維持し、今回の修正ファイルをステージへ追加した。コミット・pushは行っていない。

## Goコーディング規約の更新反映

2026-10-20更新の共通方針 `07_Goコーディング規約.md` を確認し、Agentの公開宣言と認証・保存・常駐実行・状態収集の説明へ反映した。
識別子名から始まるdoc comment、公開フィールドの意味、失敗条件・副作用・リソース解放責任を、実装から読み取れる契約として補足する。
未実装のcontext中断や型付きエラーによる識別を保証せず、通常の後始末が最善努力となる箇所も区別した。

| 対象 | 反映内容 |
| --- | --- |
| パッケージ・公開API | registration/doc.goとmain.goに責務を記載。Run・Client・State・Tokens・登録・認証更新の事前条件と保存責任を補足 |
| JSON入出力と保存形式 | 公開フィールドへ識別子、単位、nil・ゼロ値の意味を記載。実行状態とジャーナル段階の違いを維持 |
| 認証・ファイル保存 | URL検証、秘密値を含めないエラー、再送要求ID、権限制限、置換後の同期失敗、ロックの解放責任を記載 |
| バッチ・MQTT | 二重実行防止、結果不明、結果だけの再送、goroutine・channel・OSプロセス・接続の終了責任を記載 |
| OS別状態取得 | Linuxのcontext中断、Windowsの最小照会権限とcontext中断非対応、その他OSのサービス照会未対応を明記 |
| 規約参照 | READMEテンプレートを基にAgentのAGENTS.mdを作成し、原典参照と固有の注意事項を整理。READMEから参照 |
| 回帰防止 | doc_comments_test.goを追加。全OS向け運用コードの公開宣言・フィールドの説明と書き出し、パッケージ説明、JavaDocタグ不使用を構文解析で確認 |

doc comment検査はテスト用識別子と関数ローカルの匿名応答型を除外する。
グループ化した定数・変数は共通契約の説明を許可し、説明内容の正しさは差分レビューとgo docで確認する。
運用コードの処理・公開識別子・JSON項目名・状態値・CLI仕様は変更していない。

| 確認項目 | 結果 |
| --- | --- |
| 動作差分 | 変更前のステージ内容と運用Goコード18本を比較。コメントを除くGoトークンとビルドディレクティブの差分0 |
| gofmt | 運用コード・追加テストの整形差分なし |
| Windows | go vet、doc comment検査を含むgo test、実行ファイルのgo buildが成功 |
| Linux・Go 1.25 | CI相当コンテナでgo test -raceが成功 |
| go doc | パッケージ、登録API、認証更新APIの説明と事前条件・保存責任を取得できることを確認 |
| Markdown | AgentのAGENTS.md・READMEのMarkdownlint成功 |

実Backend・実MQTT接続の再検証は実施していない。今回の変更はコメント・規約参照・説明の構造検査に限定する。
