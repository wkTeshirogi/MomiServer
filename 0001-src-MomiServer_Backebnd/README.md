# SmartHomeWebServerBackend

SmartHomeWebServerBackend は、自宅サーバー・スマートホーム管理向けの Spring Boot バックエンドです。
フロントエンド管理画面、バッチ実行 Agent、IoT 連携、通知基盤から利用される API とデータ管理機能を提供します。

## 主な機能

- JWT 認証、リフレッシュトークン、ログアウト管理
- ユーザー、デバイス、サービス、バッチ定義、バッチスケジュールのマスタ管理
- バッチ実行リクエスト、Agent 向け実行通知、実行結果の受付
- バッチ実行ファイルの登録・取得
- 在庫品目マスタ、実在庫、実在庫操作履歴の管理
- ブラウザ通知、Discord 通知、通知ルーティング
- API Gateway 用 API Key 認証、API バージョン、レート制限
- MQTTによるAgent向けバッチ通知、Dynamic Securityでの専用認証・ACL・失効同期（既定無効、導入設定が必要）
- OpenAPI / Swagger UI
- Flyway による DB マイグレーション
- jOOQ による DB アクセスコード生成

## 技術スタック

| 項目 | 内容 |
|---|---|
| Language | Java 25 |
| Framework | Spring Boot 3.5.0 |
| Build | Gradle Wrapper |
| Database | PostgreSQL |
| Migration | Flyway |
| DB Access | jOOQ 3.20.3 |
| Auth | Spring Security, JWT |
| Batch | Spring Batch |
| API Docs | springdoc-openapi |
| Test | JUnit 5, Mockito, Spring Boot Test, Testcontainers |

## ディレクトリ概要

| パス | 用途 |
|---|---|
| `src/main/java` | アプリケーション本体 |
| `src/main/resources/application.yaml` | アプリケーション設定 |
| `src/main/resources/db/migration` | Flyway マイグレーション SQL |
| `src/main/resources/message-catalog` | メッセージカタログ |
| `src/test/java` | 自動テスト |
| `tools/podman` | Podman / PostgreSQL 運用補助 |
| `tools/systemd` | systemd 用 env サンプル |
| `tools/messages` | メッセージカタログ生成補助 |
| `build/generated/jooq` | jOOQ 生成コード |
| `build/generated/message-catalog` | JSONから生成したカテゴリ別メッセージ定数 |

## 前提条件

- Java 25
- Gradle Wrapper を実行できる環境
- PostgreSQL 17 互換の DB
- ローカルで jOOQ を生成する場合、DB に Flyway マイグレーション適用済みであること

CI では `eclipse-temurin:25-jdk` と `postgres:17` を使用します。

## Javaの型明示

本体・テストともローカル変数に`var`を使わず、戻り値と型引数に合わせた型を記載します。
拡張for・リソース宣言・型付きラムダ引数・レコードパターンも対象です。生成コードは手編集しません。
`ExplicitLocalTypePolicyTest`で構文を検査し、文字列・コメントは型推論と区別します。

## 定期実行の配置

`@Scheduled`の入口は`scheduling.scheduler`、有効化と共通スレッドプールは`scheduling.config.SchedulingConfig`に配置します。
実行予定の展開は`batch.service.BatchSchedulePlanningService`、車検期限の一括確認は`vehicle.service.VehicleInspectionNotificationCheckService`が担当し、各Schedulerは既存Service等へ委譲します。
業務判定とトランザクションは各機能側に維持し、既存の設定キー・Cron・実行間隔は変更していません。

`app.scheduling.enabled=false`は従来どおり定期実行と起動時バッチ展開を停止します。
車検期限の起動時確認は従来どおり独立しており、停止する場合は`app.vehicle-inspection-notification.enabled=false`も指定します。
`SchedulingRegistrationTest`と`SchedulerEntryPointTest`で、11処理の登録・設定・起動時処理・委譲・トランザクション境界を検証します。

## 定数の配置

BEの定数定義は、必ずベースパッケージ直下のカテゴリにある`constants`パッケージへ配置します。
例は`agent.constants`、`batch.constants`、`vehicle.constants`で、全体共通の定義は`common.constants`です。
`service`・`config`等へは配置せず、既存のクラス名・用途別の分割・定数値は維持します。
利用元からの参照に必要な公開範囲だけを与え、可変配列・可変コレクションは公開しません。
`ConstantDefinitionPolicyTest`で配置・生成禁止・不変性を検証します。

## メッセージの定義と生成

通知・日本語APIエラー・入力検証の文面は`src/main/resources/message-catalog/messages.ja.json`で管理します。
`java.category`・`java.name`を持つ定義から、カテゴリ直下の`constants`パッケージに`<Category>MessageConstants`を生成します。
アノテーションでも使えるコンパイル時定数のため、標準のBean Validation設定を変更する必要はありません。
既存の`MessageCode`・`MessageCatalog`による文言取得と置換も引き続き使用できます。

`generateMessageConstants`はJavaコンパイル時に自動実行されます。出力先は`build/generated/message-catalog`で、手編集・commitは不要です。
文言を変更したら再ビルドしてください。生成コードはSonarQubeの対象・JaCoCoの集計から除外し、手書きコードの判定は維持します。

`visibility=public`だけを`tools/messages/generate-message-catalog.mjs`でFEへ出力します。
`private`はBE専用定義としてFE生成対象から除外する指定で、API応答に対する認可設定ではありません。
ログ、OpenAPIの説明、文書解析用の文字列、Enumの表示ラベルは今回の移行対象外です。

生成ロジックの検証は`testMessageConstantsGenerator`として通常の`test`にも組み込まれています。
カタログと定数の一致・標準バリデーション・既存文面はJUnitで検証し、FE生成の検証は`node --test tools/messages/generate-message-catalog.test.mjs`で実行できます。

## 日時とタイムゾーン

業務時刻は`common/time`の共通`Clock`で取得し、タイムゾーンは`Asia/Tokyo`に統一します。
DBのタイムゾーンなし日時・業務日付・Cronと、Hikari接続のDBセッションを同じ日本時間へ揃えます。
JWT等の絶対時刻は`Instant`を維持し、処理時間の計測には引き続き`System.nanoTime()`を使用します。

Spring管理クラスへ`Clock`をコンストラクタ注入し、テストでは`Clock.fixed`へ置き換えられます。
現在日時の値自体をキャッシュしたり、各クラスでシステム時計を生成したりしません。
既存日時・型・データは書き換えません。別タイムゾーンで保存した履歴がある場合は、導入前に互換性を確認してください。
今回の共通化にDBマイグレーションは不要ですが、設定反映にはバックエンドの再起動が必要です。

## ページングとトークンハッシュ

`common/pagination`で開始位置・件数・先読み・ページ組立てを共用します。
通知は未指定20件・最大100件へ補正、APIログは1～200件、実行ログは20・50・100件、外部在庫は1～100件という既存契約を維持します。
総件数はDB照会結果を使用し、公開APIのレスポンスやマスタ一覧の取得方式は変更しません。

`common/security/TokenHashes`はトークン等のUTF-8文字列をSHA-256・小文字16進64文字へ変換し、計算済みハッシュを定時間比較します。
既存セッション・Agent・外部APIキーの保存形式は維持し、再発行・データ移行は不要です。
パスワードのBCrypt、JWT署名、AES暗号化、ファイルのチェックサムは別処理として維持します。

## 環境変数

ローカル起動時は、未指定の場合 `application.yaml` のデフォルト値が使われます。
本番・共有環境では秘密情報を環境変数または secret 管理へ移してください。

| 変数 | 既定値 | 用途 |
|---|---|---|
| `SERVER_PORT` | `8080` | HTTP ポート |
| `DB_HOST` | `localhost` | PostgreSQL ホスト |
| `DB_PORT` | `5432` | PostgreSQL ポート |
| `DB_NAME` | `zunda` | DB 名 |
| `DB_USERNAME` | `zunda_user` | DB ユーザー |
| `DB_PASSWORD` | `zunda_pass` | DB パスワード |
| `JOOQ_DB_URL` | `jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}` | jOOQ 生成用 JDBC URL |
| `JOOQ_DB_USER` | `DB_USERNAME` 相当 | jOOQ 生成用 DB ユーザー |
| `JOOQ_DB_PASSWORD` | `DB_PASSWORD` 相当 | jOOQ 生成用 DB パスワード |
| `JWT_SECRET` | 開発用文字列 | JWT 署名鍵 |
| `APP_CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | CORS 許可オリジン |
| `GATEWAY_ENABLED` | `true` | Gateway 機能の有効化 |
| `GATEWAY_API_KEY_ENABLED` | `true` | Gateway API Key 認証の有効化 |
| `AGENT_RESPONSE_ENCRYPTION_KEY` | 空 | Agent認証の復旧応答用AES鍵。32バイト乱数のBase64。未設定時は登録・更新不可 |
| `GATEWAY_IOT_API_KEYS` | 空 | IoT API Key 一覧 |
| `GATEWAY_RATE_LIMIT_ENABLED` | `false` | Gateway レート制限の有効化 |
| `MQTT_BROKER_URL` | `tcp://localhost:1883` | MQTT Broker URL |
| `DISCORD_NOTIFICATION_ENABLED` | `false` | Discord 通知の有効化 |
| `DISCORD_WEBHOOK_URL` | 空 | Discord Webhook URL |

## ローカル DB

PostgreSQL の接続既定値は以下です。

| 項目 | 値 |
|---|---|
| Database | `zunda` |
| User | `zunda_user` |
| Password | `zunda_pass` |
| Port | `5432` |

Podman 関連の補助ファイルは `tools/podman` 配下にあります。
利用する場合は、同ディレクトリの README と compose / quadlet 設定を確認してください。

## セットアップ

1. PostgreSQL を起動します。
2. 必要に応じて DB 接続用の環境変数を設定します。
3. Flyway マイグレーションを適用します。
4. jOOQ コードを生成します。
5. アプリケーションを起動します。

```powershell
.\gradlew.bat flywayMigrateOnly
.\gradlew.bat generateJooq
.\gradlew.bat bootRun
```

Git Bash / WSL / Linux では以下を使用します。

```bash
./gradlew flywayMigrateOnly
./gradlew generateJooq
./gradlew bootRun
```

## よく使う Gradle コマンド

| コマンド | 用途 |
|---|---|
| `.\gradlew.bat flywayMigrateOnly` | Flyway マイグレーションのみ実行 |
| `.\gradlew.bat generateJooq` | DB スキーマから jOOQ コードを生成 |
| `.\gradlew.bat test` | テスト実行 |
| `.\gradlew.bat bootRun` | ローカル起動 |
| `.\gradlew.bat bootJar` | 実行可能 jar を作成 |
| `.\gradlew.bat clean generateJooq test` | CI 相当のテスト確認 |
| `.\gradlew.bat clean generateJooq bootJar` | CI 相当の jar 作成確認 |

## API 確認

ローカル起動後、以下で OpenAPI を確認できます。

| 用途 | URL |
|---|---|
| Swagger UI | `http://localhost:8080/swagger-ui.html` |
| OpenAPI JSON | `http://localhost:8080/v3/api-docs` |
| Health | `http://localhost:8080/actuator/health` |

## API グループ

| ベースパス | 用途 |
|---|---|
| `/api/auth` | ログイン、リフレッシュ、ログアウト、ユーザー設定 |
| `/api/master/user` | ユーザーマスタ |
| `/api/master/device` | デバイスマスタ |
| `/api/master/service` | サービスマスタ |
| `/api/master/batch` | バッチ定義マスタ |
| `/api/master/batch/schedule` | バッチスケジュールマスタ |
| `/api/batch/executions` | バッチ実行リクエスト |
| `/api/v1/agent/batch/executions` | Agent 向けバッチ実行通知 |
| `/api/stock` | 実在庫、実在庫操作、実在庫履歴 |
| `/api/master/item` | 在庫品目マスタ |

## DB / API のコード値方針

ステータス、種別、状態など DB・API で保存または送受信する値は、原則として英語の安定したコードを使用します。
日本語は画面表示ラベル、通知文言、ドキュメント説明に限定します。

例:

| 種別 | コード例 |
|---|---|
| ユーザーロール | `ADMIN`, `OPERATOR`, `USER`, `BAN_USER` |
| 有効 / 無効 / 削除 | `active`, `disabled`, `deleted` |
| バッチ実行状態 | `queued`, `dispatched`, `running`, `success`, `failed`, `timeout`, `cancelled`, `expired` |
| 在庫状態 | `available`, `low`, `out_of_stock`, `deleted` |
| 在庫操作種別 | `inbound`, `outbound`, `stocktake`, `discard`, `delete` |

## 権限モデル

既存の `ADMIN` / `USER` / `BAN_USER` に加えて、日常運用操作向けに `OPERATOR` を使用します。

| ロール | 用途 |
|---|---|
| `ADMIN` | マスタ編集、設定変更、ユーザー管理、全操作 |
| `OPERATOR` | バッチ実行、サービス操作、在庫操作などの日常運用 |
| `USER` | 参照中心の一般利用 |
| `BAN_USER` | アクセス不可 |

実装上は、管理者専用 API には `@AdminOnly`、管理者または運用担当者に許可する操作系 API には `@CanOperate`、バッチ定義ごとの実行判定には `@CanExecuteBatch` を使用します。

## バッチ実行基盤

V21で実行要求へ設定スナップショット、開始・結果待ち期限、キャンセル要求、確認状況を追加します。
スケジュール実行要求は毎日00:00とアプリケーション起動完了時に作成します。
サーバーのタイムゾーンを基準に当日00:00以上・翌々日00:00未満のCron予定を展開し、有効期間内の各予定につき1件を登録します。
スケジュールIDと実行予定日時の一意制約により、再起動や複数インスタンスによる重複作成を防ぎ、既存要求は上書きしません。
作成処理の基準日時（秒単位）より前の未作成分は `expired` / `confirmed` として理由付きで記録し、配信・実行しません。
この未実行分は実行要求に記録し、実行履歴や期限超過通知は作成しません。
作成処理の開始・完了と作成件数・重複件数・失敗件数はINFOログへ記録します。
要求再配信・期限監視・キャンセル再配信は各既定30秒で動作します。
旧 `batch.execution.schedule-scan-delay-ms` は使用しません。
各間隔は前回処理完了からの待機時間です。
手動・スケジュール要求はキューへの登録で応答し、MQTT配信は定期ディスパッチャーが担当します。
スケジュール要求は実行予定時刻以降にのみ配信し、手動要求は予定時刻による待機を行いません。
事前作成済みの要求は開始待ち期限内で配信・再配信します。通常のポーリング遅延は許容し、過去分の新規補完とは区別します。
日中のスケジュール追加・変更は次の00:00または再起動時の作成処理に反映します。作成済み要求の設定値は変更しません。
4スレッドで定期処理を分離し、MQTT待機によって期限監視やスケジュール走査が停止することを防ぎます。

| 確認状況 | 実行状態と扱い |
| --- | --- |
| `pending` | 通常の配信・開始・結果待ち |
| `start_unconfirmed` | 配信試行済みの要求について開始を確認できない。実行状態は維持 |
| `result_unconfirmed` | 結果待ち期限超過。実行状態は `running` を維持 |
| `stop_unconfirmed` | キャンセル停止猶予超過。停止確認まで実行状態を維持 |
| `confirmed` | 確定した終了結果、未配信キャンセル、または未開始期限切れ |

確認状況の変化時に `system_notice` / `warning` / 発生元 `batch_execution` として通知します。
通知ルート設定・ユーザー別設定・通知全体設定が適用されます。同じ未確認状態の定期通知は繰り返しません。
結果・開始・停止が未確認であることを理由に、バッチを新しい実行IDで自動再実行することはありません。

要求作成時に共通設定を保存し、タイムアウトはバッチ定義の正の個別値を優先します。
個別値が0または未指定なら標準タイムアウトを使います。
結果待ち期限が適用タイムアウト以下の要求は作成を拒否します。
`max_retry_count` は既存項目として保持し、結果再送用の `result_retry_count` とは区別します。
開始待ち期限の起点はスケジュール要求では実行予定日時、手動要求では要求作成日時です。
結果待ち期限の起点は最初の開始通知受理時です。
重複開始通知で起点・期限を延長せず、重複完了通知で履歴を追加しません。
異なる確定結果による上書きは拒否します。

MQTT送信前に配信試行をコミットし、送信失敗・応答不明でも `queued` へ戻しません。
`dispatched` は「配信試行済み、開始未確認」を含みます。
開始期限までは同じ実行IDを30秒以上の間隔で再配信します。
配信試行が一度もない期限切れ要求のみサーバーで `expired` にし、
試行済み要求はエージェントの未開始確認を待ちます。
既存の配信試行履歴も保守的に扱い、送信されなかったと断定しません。

| API | 内容 |
| --- | --- |
| `POST /api/batch/executions/{executionId}/cancel` | 管理者がキャンセル要求。未配信なら即時確定、それ以外は停止確認待ち |
| `POST /api/v1/agent/batch/executions/{executionId}/start` | 開始通知。任意のJSON本文に `started_at` を指定可能 |
| `POST /api/v1/agent/batch/executions/{executionId}/complete` | 確定結果通知。同一結果の再送は受理済み結果を返す |
| `GET /api/v1/agent/batch/executions/{executionId}/state` | 受理済み実行状態、確認状況、キャンセル要求の照合 |
| `GET /api/v1/agent/batch/settings` | 設定バージョンを含む最新の共通設定 |

新しい設定・状態参照APIは既存の有効なBearer認証を必要とします。
開始・完了・状態・ファイル取得APIはAgent専用Bearerで認証し、端末IDと登録世代で認可します。管理ユーザーJWT・外部APIキーは代用できません。

完了通知は次の条件を満たす必要があります。

- `timeout`: `stop_confirmed=true` が必要です。
- `cancelled`: キャンセル要求が存在し、`stop_confirmed=true`、
  または開始未受理かつ `not_started=true` が必要です。
- `expired`: 開始期限を超え、開始未受理かつ `not_started=true` が必要です。
- キャンセル要求中でも正常・異常終了結果が先に確定した場合、その結果を採用します。

Agent向けTopicは `smarthome/agents/ma-{Agent UUID}-{generation}/batch/command` と `.../cancel` です。Dynamic Security同期済みの端末・世代だけに配信します。導入設定とrunの使い方は設計リポジトリの `docs/07_OperationsDesign/0004_agent_batch_runtime.md` を参照してください。
本文は `command=cancel`、`execution_id`、`cancel_requested_at`、`cancel_grace_seconds` です。
Topicは `mqtt.topics.batch-cancel` で変更できます。確定結果受理まで同じ要求を再配信します。

エージェント側では、設定の起動時・再接続時・5分ごとの取得、最終取得設定の保持、
同時実行数制御、開始期限チェック、重複実行防止、子プロセスを含む停止処理、結果の保存・再送を実装してください。
キャンセルが実行要求より先に届いた場合も実行IDの停止要求を記録し、後着要求を実行しない必要があります。
同時実行数の変更は最新設定を次の開始判定から適用し、実行中プロセスを停止しません。
要求ごとのタイムアウト・再送条件は保存済みスナップショットを使用します。
サーバー側だけでは端末上のプロセス停止・実行数制限を保証できません。

既存実行要求には移行時の共通設定を補完します。旧開始日時を最初の受理日時として扱い、
過去の `timeout` 等の確定結果は書き換えません。

## CI/CD

GitLab CI は `.gitlab-ci.yml` で管理しています。

| Job | 内容 |
|---|---|
| `unit_test` | PostgreSQL にマイグレーションを適用し、`clean flywayMigrateOnly generateJooq test` を実行。ZAP実行時は検証用bootJarも保存 |
| `sonarqube_analysis` | mainへのpush（MRマージを含む）後、JUnit成功時に既存SonarQubeプロジェクトを解析 |
| `spotbugs_analysis` | MRのみ。本番コードを解析し、検証・パス正規化したSARIFを保存 |
| `reviewdog_spotbugs` | MRのみ。SARIFから変更行の指摘をディスカッションへ投稿 |
| `dependency_check` | `RUN_DEPENDENCY_CHECK=true`を指定したmainのスケジュールだけで依存ライブラリの既知の脆弱性を検査 |
| `dependency_check_issue` | 保護されたmainのDependency-Checkスケジュールで、成功・失敗の結果を固定Issueへ記録 |
| `zap_baseline` | 保護されたmainの明示実行で、一時DBとバックエンドを起動してパッシブ検査し、終了後に破棄 |

CI では DB 起動待ち後、`src/main/resources/db/migration/V*.sql` をバージョン順に適用してから jOOQ 生成とテストを実行します。

### JUnitのDB接続プール

`unit_test`だけで、HikariCPの最大接続数を5、最小待機数を1に設定します。
複数のSpringテストコンテキストは実行中にキャッシュされ、それぞれ別の接続プールを保持します。
既定のプールを使うとPostgreSQLの100接続を使い切り、`FATAL: sorry, too many clients already`により
後続コンテキストのFlyway初期化と多数のテストが連鎖して失敗するためです。

設定には`SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE`と`SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE`を使用します。
製品の`application.yaml`、他のCIジョブ、PostgreSQLサーバーの接続上限は変更しません。
Gradleは失敗時の例外を省略せず表示し、JUnit XML・HTMLと併せて根本原因を確認できるようにします。
テストのスキップ・失敗無視・Springのコンテキスト失敗判定の緩和は行いません。

### mainマージ後のSonarQube解析

- `sonarqube_analysis`は`CI_PIPELINE_SOURCE == "push"`かつ`CI_COMMIT_BRANCH == "main"`でのみ自動実行します。MRマージによるmain更新とmainへの直接pushが対象です。MR・他ブランチ・タグ・スケジュール・画面からの手動パイプラインでは実行しません。
- `unit_test`の成功後に、コンパイル済みクラス、リソース、生成jOOQソース、JUnit XMLをアーティファクトから取得して`./gradlew --no-daemon sonar`を実行します。DB起動やコンパイル・テストを二重実行しません。これらの生成物は引き続き`build/`配下で、Git管理対象にする必要はありません。
- SonarScanner for Gradleのバージョン、プロジェクトキー`momiServer`、生成jOOQソースの除外設定は既存のままです。CIだけ`sonar.host.url`をHTTPSの`SONAR_HOST_URL`で明示的に上書きし、ローカル用Gradle設定は変更しません。
- 接続・認証・解析失敗はジョブ失敗として扱います。Quality Gateのサーバー側処理完了を最大300秒待機し、不合格・タイムアウトもCIへ反映します。
- 同じmain解析ジョブは`resource_group: sonarqube-main`で同時実行を防ぎます。

バックエンドのプロジェクト内の**「設定」→「CI/CD」→「変数」**に以下を登録してください。管理者エリアのインスタンス変数ではなく、プロジェクト変数を使用します。

| 変数 | 設定 |
| --- | --- |
| `SONAR_TOKEN` | SonarQube側で発行した`momiServer`用のプロジェクト解析トークン。タイプは「変数」、可視化は「マスクして非表示」、変数の保護はON、環境スコープは`sonarqube-main` |
| `SONAR_HOST_URL` | 通常は登録不要。既定の`https://sonar.zundamochi.local`と異なる場合だけHTTPSのURLを設定 |
| `SONAR_CA_CERT_FILE` | 任意。SonarQube用CAがGitLab Runner配布のCAと異なる場合、信頼済みCA証明書（PEM。複数可）をタイプ「ファイル」、環境スコープ`sonarqube-main`で登録 |

`SONAR_TOKEN`はGitLabのアクセストークンとは別です。SonarQubeで発行し、有効期限と対象プロジェクトの解析権限を確認してください。既存の同名変数が全環境向けの場合は、他の利用先を確認したうえでスコープを限定し、JUnitやMRジョブへの配布を防いでください。
mainのブランチ保護を維持してください。環境スコープを設定できない場合は、投稿用トークンと同様に、秘密情報を隔離する構成を先に見直してください。

CAは`SONAR_CA_CERT_FILE`を優先し、未指定ならRunnerの`CI_SERVER_TLS_CA_FILE`を利用します。指定されたPEM内の証明書を、JDK標準CAも保持した一時PKCS12 TrustStoreへ取り込み、Scannerへ渡します。証明書検証を無効化せず、一時ファイルは処理終了時に削除します。どちらも未指定ならScanner標準の信頼ストアを使用します。
既存の手動・外部ジョブでmain解析を自動実行している場合は、今回のジョブと二重送信しないよう運用を一本化してください。

ローカルではYAML解析、実行条件18パターン、ジョブshellの失敗判定、実CAのTrustStore取り込みと後片付け、Java 25でのコンパイル、`sonar --dry-run`を確認しています。認証付きのCI Lint、実SonarQubeへの解析送信とサーバー側の結果確認は、変数登録後にmainパイプラインで実施してください。

### MR静的解析の構成

- SpotBugs Gradle Pluginは`6.0.9`、SpotBugsエンジンは`4.9.8`、reviewdogは`0.21.1`に固定しています。
- Pluginはローカルに存在するソースでGradle 7.1以上の検査、Worker APIとJava toolchain対応を確認して選定しています。JavaExec旧APIを使用する非Worker実装には切り替えません。
- エンジンはローカル配布物に含まれるASM 9.9とSARIF 2.1.0出力を根拠に、Java 25のクラス解析用として選定しています。無条件に最新版を採用したものではありません。
- 解析対象は`src/main/java`に対応する本番クラスです。生成jOOQクラスは除外し、参照用クラスパスには残します。テストコードは解析しません。
- 通常の`spotbugsMain`と`check`は問題検出時に失敗します。SpotBugsの全体設定で失敗を無視しません。既存のCheckstyle設定は追加・変更していません。
- MR専用`spotbugsMr`はエンジンの終了値を直接確認し、`0`（問題なし）と`1`（問題検出）のみ投稿へ進めます。`2`（クラス不足）、`4`（解析エラー）やそれらを含む終了値は解析ジョブを失敗させます。
- `tools/ci/spotbugs_sarif.py`はSARIFの実行成功状態とファイル・行番号を確認し、URI baseを解決してリポジトリ相対のパスへ変換します。未解決のファイルや不完全な解析結果は投稿しません。
- 出力は`build/reports/spotbugs/`配下の`main.raw.sarif`（解析原本）、`main.sarif`（投稿用）、`exit-code.txt`です。通常タスクの出力は`local.sarif`です。CIアーティファクトは`when: always`で1週間保存します。
- MR向け2ジョブは`CI_PIPELINE_SOURCE == "merge_request_event"`の場合のみ実行し、`GIT_DEPTH: "0"`を指定します。SonarQubeのmain解析は前節の専用ジョブで実行し、MRへSonarトークンを渡しません。

### セキュリティ検査

#### 概要・対象

- FindSecBugs `1.14.0`をSpotBugsのプラグインとして追加しています。Spring向けのセキュリティ検出器を持つ公式リリースを採用し、既存のSpotBugs `4.9.8`とJava 25で実行確認しています。
- 通常の`spotbugsMain`に加え、独自タスク`spotbugsMr`・`spotbugsSonar`にも同じプラグインを読み込ませます。解析対象・生成コード除外・既存の失敗判定は変更しません。
- MRは既存の`SpotBugs`名でreviewdogへ投稿し、mainは既存XMLからSonarQubeへ取り込みます。指摘0件時のディスカッション解決処理も維持します。FindSecBugs導入で既存コードの指摘が増える場合があります。安全性を確認せず一括除外しないでください。
- OWASP Dependency-Check Gradle Pluginは`13.0.0`に固定しています。公式のGradle 9.x・Java 11以上の互換範囲を確認したうえで採用しています。
- Dependency-Checkは本番`runtimeClasspath`の直接・推移的依存を検査します。テスト・ビルドツールの依存やチェックアウト全体は対象外です。別認証が必要なOSS Indexは使用せず、NVDを脆弱性情報源にします。
- ZAPは公式安定版`2.17.0`のLinux/amd64イメージをdigest付きで固定しています。認証前のHTTP(S)ページを対象とするbaseline検査のみで、アクティブ検査は実行しません。

#### 2026-10-23収集報告との照合

対象報告はMomiServerの依存検査結果ではなく、複数エコシステムのアドバイザリー750件の収集結果。実際に解決した本番・テスト・Gradleビルド用依存と照合して対応する。未使用製品の指摘を一括して導入・更新したり、検出除外で隠したりしない。

| 対象 | 対応と範囲 |
| --- | --- |
| Jackson | `gradle.properties`の`jacksonBomVersion=2.22.3`を本番・テストとGradleプラグインの両方へBOMで適用する。旧本番2.19.0、旧ビルド用2.22.1の混在を解消する。対象はCVE-2026-19032、59889、68494、68497、77310、83557、89407、89425、91776、91777。個々の脆弱性には非同期解析・特殊な型やアノテーションなどの発生条件があり、バージョン一致だけで全件の攻撃到達を証明したものではない。 |
| Gradleビルド用依存 | Bouncy Castle `bcprov-jdk18on`を1.78.1から修正バックポート1.80.2（CVE-2025-14813）、jsoupを1.23.1から1.23.2（CVE-2026-75140）へ制約する。アプリのHTTP経由での攻撃到達は未確認だが、解析ツールの推移的依存にも修正を適用する。 |
| Spring Framework | CVE-2026-47884の`XsltView`と暗黙ビュー解決、CVE-2026-47890のSSEビュー断片は現在未使用。現在のJSON REST/WebSocket処理には該当条件を確認していない。6.2系の修正版6.2.20は商用配布のみのため、Spring 7/Boot 4へ機械的に更新しない。これらの機能を追加する前には修正版への移行が必要。依存バージョン自体のスキャン指摘は残り得る。 |
| Python CI | Issue投稿ジョブを公式`python:3.12.14-slim`へ固定し、CVE-2026-7210の修正を含むパッチ版を使う。OSパッケージやZAP同梱Pythonは別の実行環境であり、この指定だけでは更新されない。 |
| Java実行環境 | 報告のOracle JDK 25のCPEをTemurin全体への脆弱性と断定しない。既存のZAP用Temurin JRE 25.0.4.1+1は維持する。各運用端末のJDK/JREベンダー・パッチ版は別途確認し、古いJava 25初期版を使用しない。 |

Jacksonの不正DataInputトークン・分割数値の制限回避と正常JSONを`JacksonSecurityRegressionTest`で検査する。アプリのAPI JSON契約、Spring起動、既存の認証・DB処理は既存JUnitで確認する。参考: [Jacksonの修正情報](https://github.com/FasterXML/jackson-core/security/advisories/GHSA-7hhh-6rmp-j9qf)、[Spring XSLTの条件と修正配布範囲](https://spring.io/security/cve-2026-47884/)、[Spring SSEの条件](https://spring.io/security/cve-2026-47890/)。

隔離したLinux/Java 25・PostgreSQL 17で37マイグレーション、jOOQ生成、JUnit 561件（559成功・2スキップ、新規4件は全成功）、JaCoCo、`bootJar`、`spotbugsSonar`が成功した。旧Jacksonでは新規テスト中2件の失敗と正常系の成功を確認してから更新した。CI投稿用Pythonの単体テスト37件、CI YAML解析も成功した。実GitLab/SonarQubeへの送信や長時間のNVD再取得はこの確認には含まない。

#### Dependency-Checkの実行

ローカルでは`./gradlew --no-daemon dependencyCheckAnalyze`を実行します。DB・jOOQ生成・コンパイルは不要です。通常タスクはCVSS 7以上で失敗し、`-PdependencyCheckReportOnly=true`を指定した実行だけ、脆弱性検出をレポート出力に限定します。`check`へ外部通信を伴う依存は追加していません。

CIは初期棚卸しのためレポートのみです。`DEPENDENCY_CHECK_ENFORCE=true`をプロジェクト変数またはスケジュール変数として設定するとCVSS 7以上で失敗します。無効な設定値、依存解決・データ更新・解析エラーはレポートのみの実行でも失敗します。`allow_failure`や全体の失敗無視は使用しません。

Dependency-Checkは`main`のパイプラインスケジュールで`RUN_DEPENDENCY_CHECK=true`が指定された場合だけ実行します。通常のpush・MR・「新しいパイプライン」からの実行では起動しません。JUnit・MRのSpotBugs/reviewdog・mainへのpush後のSonarQubeは通常どおり維持します。Dependency-Check専用スケジュールではJUnitを省略し、PostgreSQLの起動・マイグレーション・jOOQ生成・アプリのビルドも行いません。

1. [NVDのAPIキー申請](https://nvd.nist.gov/developers/request-an-api-key)からキーを取得します。
2. プロジェクトの「設定」→「CI/CD」→「変数」に`NVD_API_KEY`を登録します。可視化は「マスクして非表示」、環境スコープは`dependency-check`です。値をGradle引数やGitへ記載しないでください。
3. スケジュールの所有者が保護された`main`へマージ可能であり、保護変数の利用条件を満たすことを確認してください。キーなしでも実行できますが、初回更新には長時間かかり、レート制限の可能性があります。スケジュール変数へキーを複製せず、既存のCI/CD変数を使用します。
4. 以下の専用スケジュールを登録します。コード変更がなくても新規公表CVEを再検査できます。既存のDependency-Check用スケジュールがある場合は新規作成せず編集してください。
5. 指摘の実際の利用状況と誤検知を整理してから厳格モードへ切り替えます。例外が必要な場合は、依存・CVE・根拠・期限を限定した抑制ルールをレビューで追加してください。初期の一括抑制はありません。

#### Dependency-Check専用スケジュール

「ビルド」→「パイプラインスケジュール」→「新しいスケジュール」で以下を設定します。スケジュールはGitLab側の登録データであり、`.gitlab-ci.yml`の変更だけでは作成されません。

| 項目 | 設定 |
| --- | --- |
| 説明 | `Dependency-Check weekly` |
| 間隔パターン | 毎週日曜日03:00、カスタム`0 3 * * 0` |
| タイムゾーン | `Asia/Tokyo` |
| 対象ブランチ | `main` |
| 変数 | `RUN_DEPENDENCY_CHECK=true` |
| 変数 | `RUN_ZAP_BASELINE=false`（ZAP用スケジュールとは分離） |
| 変数 | `DEPENDENCY_CHECK_ENFORCE=false`（初期棚卸し。厳格化時は`true`） |
| 有効 | このCI変更が`main`へ反映された後に有効化 |

既存スケジュールにも`RUN_DEPENDENCY_CHECK=true`の追加が必要です。既存のZAP用スケジュールは変更しません。両方のフラグを`true`にした場合はJUnit・Dependency-Check・ZAPを実行するため、専用実行にはなりません。主キーやAPIトークンなどの秘密情報をスケジュール変数へ追加する必要はありません。

反映後、登録したスケジュールの「実行」ボタンで即時確認できます。この手動実行はスケジュールとして実行されます。`dependency_check`と後続の`dependency_check_issue`が起動すること、診断・解析レポートとIssueコメント、次回の実行日時を確認してください。[GitLab公式の登録・手動実行手順](https://docs.gitlab.com/ci/pipelines/schedules/)も参照してください。

スケジュール化はMR・pushの待ち時間を減らしますが、NVDの通信障害や初回全件取得自体を解消する変更ではありません。キャッシュは既存どおりブランチ別なので、`main`で未作成なら初回取得が必要です。成功後の継続実行では同じ`main`用キャッシュを利用し、更新時点からの差分取得が期待できます。キャッシュ削除・失敗・Runner変更時の再取得には注意してください。

週次実行のため、新しく公開されたCVEの検知は次回実行まで遅れることがあります。緊急時はスケジュールの「実行」で追加検査してください。GitLabのスケジュールワーカーやRunnerの待ち時間により、実際の開始は指定時刻より遅れることがあります。

NVDキャッシュは`.dependency-check/`でGit管理外です。CIはバージョン・ブランチ別キーを使用し、同時更新を`resource_group`で防止します。Runnerの保護/未保護キャッシュ分離を維持してください。キャッシュは成功・失敗の両方で保存し、4時間経過後に再更新します。更新完了時点は全ページの取得・DB処理後に保存されるため、初回取得が中断された場合は、DBが残っても全件を再要求する可能性があります。表示された取得件数からの再開は保証しません。同一コードでも解析タスクは毎回実行し、依存一覧が空のレポートは成功扱いしません。

プラグイン13.0.0ではタスクの保存先初期値が拡張設定より優先されるため、解析・集約・更新・削除の各タスクへ保存先Providerを明示的に設定しています。従来は`.gradle/dependency-check-data/11.0`へ保存され、GitLabの`.dependency-check/`キャッシュに含まれない状態でした。CIは解析直前に設定の回帰試験を実行し、実際の保存先をログへ表示します。手動確認は`./gradlew --no-daemon --init-script tools/ci/test_dependency_check.init.gradle testDependencyCheckConfiguration`です。プラグイン更新時もこの確認を維持してください。

本解析の`timeout`コマンドは撤廃し、取得に時間がかかっても解析プロセスを独自の時間制限では停止しません。GitLab CI自体は無制限にせず、`dependency_check`のジョブ上限を`48h`としています。ジョブ単位の上限はプロジェクトの既定値を上書きしますが、Runner側に短い上限があればそちらが優先されます。48時間でも不足する場合はジョブとRunnerの両方を見直してください。

共通ダウンローダーの接続待ち15秒・読込待ち60秒と、NVD APIの再試行上限10回は維持します。これらは無応答接続から復帰するための通信単位の設定で、解析全体の制限ではありません。APIクライアント固有の待機・再試行処理もあります。通信障害や再試行上限超過は引き続き失敗として扱い、無期限に再送する変更は行いません。

`tools/ci/dependency_check_run.sh`は本解析の終了を待ち、5分ごとにUTC時刻付きの生存確認メッセージを出します。GitLabの1時間無出力による停止を防ぐためのもので、取得件数・進捗・通信成功を示すものではありません。APIキー・実行引数は出力せず、実際の進捗は従来どおり`Downloaded ...`等のログで確認します。待機中に処理が進まない場合でも生存確認は出るため、必要に応じて利用者がキャンセルしてください。

間隔はジョブ変数`DEPENDENCY_CHECK_HEARTBEAT_SECONDS`（既定`300`秒）、キャンセル時の終了猶予は`DEPENDENCY_CHECK_SHUTDOWN_GRACE_SECONDS`（既定`5`秒）です。追加のCI/CD変数登録は不要です。正常・異常終了時に定期出力を停止し、TERM/INT時は解析のプロセスグループへ終了要求を送り、猶予後も残る子プロセスだけを強制停止します。終了猶予は解析の時間制限ではありません。Linux/Bash向けで、追加パッケージは不要です。

長時間実行中はRunnerの実行枠とNVD更新用`resource_group`を使用します。同時実行枠が1つのRunnerでは他ジョブが待機するため、必要に応じて専用Runnerや並列実行枠を用意してください。NVD処理を同時実行するために`resource_group`を外す変更は行いません。

解析プロセスが終了すれば、終了コードを`build/reports/dependency-check-ci/analysis-exit-code.txt`へ保存して表示し、非ゼロならCIを失敗させます。`cache-files.txt`にはキャッシュ直下のファイル名・サイズ（またはディレクトリ未作成）だけを記録します。DB本体は通常のキャッシュにのみ保存し、アーティファクトへは追加しません。GitLab/Runnerの上限や強制停止・キャンセル時には後処理・転送を実行できない可能性があります。後続Issue報告は起動できればGitLabのジョブ状態からタイムアウト等を判定します。

解析前にJava 25の標準HTTPクライアントで、公式NVD APIへ`resultsPerPage=1`の診断リクエストを1回だけ送ります。接続待ち15秒・リクエスト30秒・プロセス全体45秒（終了猶予5秒）で制限し、APIキーは環境変数からヘッダーにだけ設定します。リダイレクトは追跡せず、TLS検証を維持します。キーの有無・HTTPステータス・所要時間・通信失敗の分類を`build/reports/dependency-check-ci/nvd-connectivity.properties`とジョブログへ記録します。キーの値、応答本文、ヘッダー、例外メッセージは出力しません。

| 診断結果 | 確認対象 |
| --- | --- |
| `httpStatus=200` | 診断リクエストへの応答あり。全件更新・解析完了やキーの有効性を保証する結果ではない |
| `httpStatus=403` | NVD側の拒否応答。キーだけでなくアクセス制限なども確認する |
| `httpStatus=429` | リクエスト制限。同じキーを使う別ジョブ等も含めて確認する |
| `httpStatus=500`、`502`、`503`、`504` | NVDまたは経路上のサーバーエラー |
| `outcome=dns_error`、`tls_error`、`timeout` | Runnerの名前解決、CA・TLS経路、通信待ちを切り分ける |
| `outcome=connection_or_io_error` | 接続拒否・切断など、上記以外の通信エラー |

診断失敗だけでは解析を止めず、実際のDependency-Checkに更新・再試行を行わせます。診断用JavaとGradle内のAPIクライアントは別プロセス・別実装なので、診断成功だけで後続処理の通信成功を保証しません。CIでは診断コードのオフライン試験も実行します。全体の`--debug`は秘密情報を含む恐れがあるため使用しません。

使用中のAPIクライアント9.0.6は再試行判定前に警告を出し、低い再試行上限では正常応答にも`NVD API request failures ...`を表示する場合があります。このため独自上限3回を撤回しています。`4th time`も4回目の再試行実行を確定するログではありません。警告単独で障害と断定せず、診断結果・取得進捗・解析の終了結果を合わせて確認します。[公式クライアントの実装](https://github.com/jeremylong/open-vulnerability-clients/blob/v9.0.6/src/main/java/io/github/jeremylong/openvulnerability/client/nvd/NvdApiRetryStrategy.java)を参照してください。

`H2StreamResetException`が続く場合の切り分けとして、CIはさらに`nvdTransportDiagnostic`を実行します。Dependency-Checkプラグインの既存クラスパスにあるApacheクライアントで、最初の2,000件をHTTP/2・HTTP/1.1それぞれ1回だけ取得します。自動再試行とリダイレクトを禁止し、TLS・ホスト名検証は通常設定のままです。各リクエストの待機上限45秒、Gradle起動を含めた比較処理全体の上限3分（終了猶予5秒）とし、失敗しても本解析へ進みます。追加ライブラリは導入していません。

比較結果は`build/reports/dependency-check-ci/nvd-transport.json`と`nvd-transport.log`です。要求したHTTPバージョン、実際の応答バージョン、HTTPステータス、受信バイト数、JSON形式のContent-Typeか否か、所要時間、例外の分類・クラス名、取得できた場合のHTTP/2エラーコードを記録します。APIキー・応答本文・応答ヘッダー・例外メッセージは保存しません。

- HTTP/2だけ失敗しHTTP/1.1で200なら、HTTP/2経路・相互運用の問題を疑う材料になります。どの装置がリセットしたかはこの比較だけでは断定できません。
- 両方失敗する場合は、大きい応答の取得時間や共通の通信経路なども確認します。
- 本解析のHTTPバージョンを自動変更する機能ではありません。Java標準クライアント用の設定がApacheクライアントにも効くと仮定して設定しないでください。
- 手動診断は`./gradlew --no-daemon nvdTransportDiagnostic`、オフライン試験は`./gradlew --no-daemon --init-script tools/ci/test_nvd_transport.init.gradle testNvdTransportDiagnostic`です。通信を伴う診断タスクを通常の`check`へは追加していません。

共有（インスタンス）Runnerに最大タイムアウトを設定している場合は、「管理者エリア」→「CI/CD」→「Runner」→`docker-shared-runner`の編集で「最大ジョブタイムアウト」を`172800`秒（48時間）以上にしてください。未設定なら変更不要です。複数Runnerがジョブを受け付ける場合は対象すべてを確認します。プロジェクトRunnerはプロジェクトの「設定」→「CI/CD」→「Runner」から編集します。ジョブ側で`48h`を明示しているため、プロジェクトの「一般パイプライン」のタイムアウトを一律に延長する必要はありません。CI/CD変数等に`RUNNER_SCRIPT_TIMEOUT`を別途設定している場合は、短い制限が残っていないことも確認してください。設定変更とmain反映後に、新しいスケジュールパイプラインで確認します。実行中ジョブの延長は保証しません。[Runner上限](https://docs.gitlab.com/ci/runners/configure_runners/#set-the-maximum-job-timeout)、[ジョブ単位の上書き](https://docs.gitlab.com/ci/yaml/#timeout)、[無出力時の制限](https://docs.gitlab.com/ci/pipelines/settings/#set-a-limit-for-how-long-jobs-can-run)も参照してください。

長時間実行用のオフライン試験は`python -B -m unittest discover -s tools/ci -p 'test_dependency_check_run.py'`です。Linux/Python 3.12で11ケース（無出力中の継続・終了コード・TERM/INT・子プロセス終了・引数非出力等）、既存を含め86テストが成功しました。CIの正常・異常10分岐、768通りの実行条件、YAML・Bash構文も確認し、他ジョブの定義・実行条件は変更していません。WindowsではLinuxのプロセスグループ試験をスキップします。実NVDの全件取得完了、48時間の連続実行、GitLabサーバー側設定・CI Lintは未確認です。CI補助処理だけの変更なのでアプリのJUnit・Gradle全体は再実行していません。

2026-10-21の実通信確認では、代替候補のNVD公式JSON 2.0年別フィードにも404応答・読込タイムアウトがあったため、自動切り替えは導入していません。NVD側の障害そのものをCI設定で解消することはできません。更新失敗が続く場合は、保存ログでHTTP応答を確認し、NVD側の復旧後に再実行してください。

#### Dependency-CheckのIssue報告

保護された`main`の専用スケジュールで、解析後に`dependency_check_issue`を実行します。`review`ステージの`when: always`と`dependencies`を使用し、失敗した解析ジョブの成果物も受け取ります。投稿ジョブはGradle・アプリを実行せず、共有キャッシュも使いません。解析ジョブの失敗は隠さず、投稿エラーもCIの失敗として扱います。

- 初回は同じプロジェクトに機密Issue「Dependency-Check定期実行結果」を自動作成し、実行ごとにコメントを追加します。タイトルと専用マーカーで照合し、同名の手動Issueは変更しません。
- 同一パイプラインの投稿リトライでは既存コメントを更新し、内容が同じなら何もしません。Issueを閉じても次回は同じIssueへ追記し、自動クローズ・再オープンはしません。管理対象が複数ある場合や機密設定が外された場合は安全のため失敗します。
- 実行日時、ジョブ状態、終了コード、解析対象数、未抑制の脆弱性の深刻度別件数、ライブラリ・CVE等の先頭50行、パイプライン・解析ジョブ・レポートへのリンクを記録します。件数は情報源と脆弱性識別子で重複排除します。
- GitLabのPipeline Jobs APIから解析ジョブを特定し、成果物内のパイプラインID・ジョブIDを照合します。成功状態・終了コード0・有効な非空レポート・解析例外なしの全条件を満たす場合だけ解析成功とします。
- 更新失敗・タイムアウト・欠落/破損/別実行の成果物は「検査未完了」とし、「脆弱性なし」と判定しません。厳格モードでのCVSS判定によるジョブ失敗も正常完了扱いにはせず、取得できた検出結果を参考値として表示します。
- 取得進捗は解析ログから数値だけを抽出します。生ログ、例外メッセージ、レポート原本、APIキーはIssueへ転載しません。Issue履歴は残りますが、リンク先のアーティファクトは1週間で失効します。

必要な手動設定は以下です。

1. 対象プロジェクトの「設定」→「アクセストークン」で投稿専用Project Access Tokenを発行します。ロールは`Reporter`、スコープは`api`、有効期限を設定してください。Issueとコメントの作成・自身のコメント編集、パイプラインジョブ情報の読み取りに使用します。個別の権限制限がある環境ではこれらのAPI権限を確認してください。
2. **プロジェクトの**「設定」→「CI/CD」→「変数」へ以下を登録します。reviewdog・SonarQube・NVDのトークンは流用しません。

| 項目 | 値 |
| --- | --- |
| キー | `GITLAB_ISSUE_API_TOKEN` |
| 値 | 発行したProject Access Token（チャット・リポジトリへ記載しない） |
| 可視化 | マスクして非表示 |
| 変数の保護 | オン |
| 環境スコープ | `dependency-check-issue`（`*`にしない） |
| 変数参照を展開 | オフ |

3. `main`の保護と、スケジュール所有者による保護変数の利用条件を確認します。投稿ジョブは保護されていないmain・MR・通常pushでは実行しません。
4. 社内CAが必要な場合は既存のRunner設定で`CI_SERVER_TLS_CA_FILE`が提供されるようにします。Pythonの既存信頼ストアに追加して検証し、TLS検証は無効化しません。API URLは`CI_API_V4_URL`を使用し、`CI_SERVER_URL`と一致するGitLabだけを許可します。認証情報を付けたリダイレクトは追跡しません。
5. mainへの反映・変数登録後、スケジュールの「実行」でIssue・コメント・リンクを確認します。失敗後も投稿ジョブが動くことと、投稿ジョブだけを再実行してコメントが重複しないことも確認してください。

環境スコープはGitLabの変数登録側で設定する必要があります。`unset`だけでは、ジョブ開始前からの変数配布を防げません。また、mainのCI設定・投稿スクリプトを変更できる人はトークンを使用できるため、保護ブランチのマージ権限とレビューを維持してください。POSTの自動再送はせず、結果不明時は投稿ジョブを再実行し、既存のIssue・コメントを照合してから処理します。

パイプライン全体のキャンセル・Runner障害では投稿自体を保証しません。解析ジョブのタイムアウトで成果物が残らなくても、投稿ジョブが起動できればGitLabのジョブ状態から未完了を記録します。期限切れ・削除済みの成果物はGitLabによるダウンロード段階で投稿ジョブが失敗する場合があります。実行直後に確認し、必要な結果はIssueの要約で保持してください。

ローカルのオフライン試験は`python -B -m unittest discover -s tools/ci -p 'test_dependency_check_issue.py'`です。CIの実行識別情報を環境変数に設定した環境では`python -B tools/ci/dependency_check_issue.py --dry-run`で投稿なしの要約確認もできます。GitLab操作には[Issues API](https://docs.gitlab.com/api/issues/)、[Notes API](https://docs.gitlab.com/api/notes/)、[後続ジョブの成果物依存](https://docs.gitlab.com/ci/yaml/#dependencies)を使用します。

2026-10-23の確認では、新規37テスト（既存を含め75テスト）、Linux/Python 3.12での実行、CIの正常・異常9分岐、768通りの実行条件、YAML・Bash構文が成功しました。機密Issueの作成・再利用、投稿リトライ時の更新、API失敗・ページング・欠損成果物・認証情報の非出力を模擬API応答で確認しています。実GitLabでの新トークンを使った投稿と、GitLab CI Lintによるサーバー側検証は未実施です。アプリ本体に変更がないためJUnit・Gradle全体は再実行していません。

#### ZAPの使い方

「ビルド」→「パイプライン」→「新しいパイプライン」で保護されたmainを選び、変数`RUN_ZAP_BASELINE=true`を追加して実行します。同じ変数を持つmainのスケジュールも利用できます。通常のpush、MR、未保護ブランチではZAPは実行しません。

検証用バックエンドはCI内で自動起動します。PC側でアプリを起動したり、LAN内のURLを指定したりする必要はありません。以前の`ZAP_TARGET_URL`、`ZAP_ALLOWED_ORIGIN`、`ZAP_TEST_ENVIRONMENT`はこのCIジョブでは使用しないため、登録済みなら他の用途がないことを確認して削除してください。FindSecBugs・Dependency-Check・既存SonarQubeの変数設定は変更ありません。

1. `unit_test`でJUnitが成功した後、ZAP実行条件に一致する場合だけ`bootJar`を生成します。
2. ZAPジョブはそのJARをアーティファクトから取得し、ジョブ専用のPostgreSQL 17（DB名`zunda_zap`）を別途起動します。JUnitのDBは引き継ぎません。
3. `tools/ci/zap_backend.py`がDBの起動受付を確認し、バックエンドを起動します。Flywayが新しいDBにマイグレーションと既存の初期データを適用します。
4. ループバックの空きポートに起動し、`/v3/api-docs`の有効なOpenAPI応答を確認してから、同じジョブ内のZAPで`/swagger-ui/index.html`を入口に検査します。外部URLへの切り替えはできません。
5. 成否にかかわらずバックエンドを停止します。停止が30秒で終わらない場合は強制終了します。CIのキャンセル時も終了処理を行い、ジョブ終了後はRunnerがサービスDBを破棄します。アプリログと検査レポートだけを保存します。

- ZAPイメージのJava 17はそのまま使い、バックエンド専用にTemurin JRE `25.0.4.1+1`のLinux/x64配布物を取得します。公式SHA-256をCIに固定し、検証後に`build/zap-runtime/`へ展開します。`JAVA_HOME`を置き換えず、アプリにだけJava 25の実行パスを渡します。実行環境はLinux/amd64が必要です。
- データソースはCI専用DBに固定します。子プロセスへCIトークン・通常環境の接続設定・JVMオプションは引き継がず、JWT署名鍵も実行ごとに使い捨てで生成します。ファイル保管先も一時ディレクトリです。
- `app.scheduling.enabled=false`で`@Scheduled`全体と起動時バッチ展開を止め、Spring Batchの起動実行・MQTT・Agent MQTT・Discord・車検通知も停止します。通常の起動では定期処理は従来通り有効です。認証・認可のSecurityFilterChainは変更しません。
- 初期実装は未認証検査です。ログイン後のページやトークンが必要なAPIは網羅しません。認証付き検査は専用テストユーザーとZAP認証コンテキストを別途設計してから追加します。認証境界のJUnitは引き続き必要です。
- HTTPリクエストは実際に発生します。既存DBや起動中アプリには接続せず、一時DBにだけ検査中のログ等を保存します。アプリとDBは常設デプロイしません。
- ZAP終了値`0`は正常、`2`は警告としてレポート保存後に成功とします。`1`のFAIL判定、`3`等のツールエラー、タイムアウト、レポート不足、対象サイトの結果不足は失敗します。警告を抑制しているのではなく、初期棚卸しとしてレポートで確認する方式です。
- ZAPが利用するプロキシ通信はブラウザーと同一のTLS検証試験ではありません。社内CAの信頼確認やTLS設定の安全性をこのジョブだけで判定しないでください。

#### 出力・注意事項

| 検査 | 出力先 |
| --- | --- |
| FindSecBugs | 既存の`build/reports/spotbugs/`内SARIF・SonarQube用XML |
| Dependency-Check | `build/reports/dependency-check/dependency-check-report.html`、`.json`、`.sarif` |
| Dependency-Check実行ログ | `build/reports/dependency-check-ci/analysis.log` |
| ZAP | `build/reports/zap/baseline.html`、`.json`、`.xml`、`exit-code.txt`、`backend.log` |

CIレポートは成功・失敗を問わず1週間保存します。`build/`をGitへ追加する必要はありません。ZAPレポートには応答内容やURL等が含まれるため、GitLabプロジェクトとアーティファクトの閲覧権限を適切に管理してください。Dependency-CheckとZAPの結果をSonarQubeへ自動取り込みする追加プラグインは導入していません。

MRから変更できるCI・Gradle・補助スクリプトは秘密情報の完全な隔離境界ではありません。既存のreviewdog/Sonarトークンは専用環境スコープを維持し、ZAP実行はレビュー済みの保護mainに限定します。ツールの検出結果だけで安全を保証せず、誤検知のレビューと権限・入力検証の回帰テストを併用してください。

参考: [FindSecBugs公式](https://find-sec-bugs.github.io/)、[Dependency-Check Gradle公式](https://github.com/dependency-check/dependency-check-gradle)、[ZAP baseline公式](https://www.zaproxy.org/docs/docker/baseline-scan/)。

#### 導入時の動作確認

- Java 25・Gradle 9.4.1で37マイグレーション、jOOQ生成、JUnit 554件（552成功・2スキップ）、JaCoCo、`bootJar`が成功しました。
- FindSecBugs込みのMR SARIF・SonarQube用XML生成、SARIF 2.1.0の相対パス検証、reviewdogのlocal読み込みを確認しました。指摘は`UNSAFE_HASH_EQUALS`が4件、`COMMAND_INJECTION`が1件で、未レビューのため除外していません。通常の`spotbugsMain`が指摘で失敗することも確認しています。
- Dependency-Checkは模擬NVD APIを使い、実際の依存JAR 94件の解析、HTML/JSON/SARIF生成、模擬CVE検出時のレポートのみ/厳格モードの分岐を確認しました。模擬結果は`build/security-verification/`に隔離し、実CVEの診断結果として扱いません。実NVD取得はAPIキー未設定の環境でエラーとなり、実データの診断完了は未確認です。
- ZAPは専用ローカル試験サイトへの実検査で警告6件と3形式のレポート保存を確認しました。対象アプリケーション全体や認証後の画面を診断済みという意味ではありません。
- 補助処理のオフライン24テスト、CI YAMLと実行条件の確認、既存Sonarタスクのdry-runを実施しています。GitLab上でのジョブ実行、MR投稿、SonarQube取り込みは、マージと変数設定後に確認してください。

#### バックエンド自動起動の動作確認

- 2026-10-21にJUnit全557件（555成功・2スキップ）、JaCoCo、`bootJar`、補助処理38テストが成功しました。定期処理の既定有効・明示有効・停止時の起動イベント非実行を回帰テストしています。
- CIと同じZAPイメージ内で、固定SHA-256のJRE取得から、空の一時DBへの37マイグレーション、バックエンド起動、OpenAPI応答待ち、ZAP検査、正常停止まで確認しました。検査後のバッチ実行要求は0件です。
- 検査ではCSP・Permissions Policy・Cross-Origin Resource Policyの警告3種類を検出しました。未認証の公開画面に対する結果であり、全APIや認証後操作を診断済みという意味ではありません。

#### NVD取得タイムアウト対策の動作確認

- Gradle 9.4.1・Java 25で設定読み込み、通信待ち時間・API再試行回数・厳格モードの設定を確認しました。
- 模擬NVDで実依存JAR 94件を解析し、HTML/JSON/SARIF 2.1.0生成、レポートのみの成功、高深刻度の模擬CVEによる通常モードの失敗を再確認しました。実NVDでの診断完了は未確認です。
- 既存CI補助処理38テスト、YAML・Bash構文、CIの正常・異常7分岐を確認しました。他ジョブの設定は変更していません。GitLab上の実行時間・キャッシュ転送は変更反映後に再確認してください。

#### NVD接続診断・進捗表示の動作確認

- Linux/Java 25で診断コードのオフライン16ケースが成功しました。200/403/429/500/503/302応答、空キー、ヘッダー不正、タイムアウト、DNS/TLS分類と、診断ファイルに模擬キー・応答本文を含めないことを確認しています。
- 検証用コンテナから公式APIへの1回のリクエストは、キーなしでHTTP 200・約0.7秒でした。GitLab Runnerや登録済みAPIキーでの確認結果ではありません。
- 模擬NVDで依存94件の実解析と3形式のレポート生成、`--info`での取得件数・処理進捗表示を確認しました。低い再試行上限による正常応答時の警告は出なくなりました。
- 既存補助処理38テスト、CIの正常・異常8分岐が成功しました。診断が失敗しても本解析を実行し、本解析の異常終了はCIへ伝搬することを確認しています。アプリ本体は変更していないため、JUnit全体は再実行していません。

#### Apache HTTPバージョン比較の動作確認

- オフライン11ケースでHTTPステータス、HTTP/1.1応答、キーの送信と非出力、リダイレクト禁止、タイムアウト、HTTP/2リセットのエラーコード抽出を確認しました。クライアントは正常な終了手順で閉じ、接続・スレッドを残しません。
- キー未設定の検証用Linuxコンテナでは、2,000件取得はHTTP/2・HTTP/1.1とも45秒でタイムアウトしました。2026-10-23のGitLab Runnerログでもキー設定ありで両方式が45秒でタイムアウトしています。HTTP/1.1への変更だけで直るとは判断できず、本解析の通信方式は変更していません。
- 既存補助処理38テスト、CIの正常・異常9分岐、他ジョブの設定不変を確認しました。診断タスクの`BUILD SUCCESSFUL`は比較結果を保存できたという意味で、NVD更新・本解析の成功を表すものではありません。

#### Dependency-Checkスケジュール化の動作確認

- YAML構文と768通りの実行条件をローカルで検証しました。専用スケジュールは`dependency_check`のみ、MR・pushでは同ジョブを除外し、SonarQube・reviewdog・ZAPの実行条件とジョブ依存が維持されることを確認しています。既存補助処理38テストも成功しました。
- 2026-10-21にGitLabのスケジュール`#1`「Dependency-Check weekly」を登録し、毎週日曜03:00・`Asia/Tokyo`・対象`main`・上記3変数の保存を画面で確認しました。このCI変更の`main`反映待ちとして非アクティブで作成しているため、反映後に編集画面の「有効化」をオンにして保存してください。
- GitLab上の実ジョブ起動は確認できましたが、実NVDの全件解析完了は未確認です。スケジュール化自体はCIの条件とREADMEのみの変更なのでアプリのJUnit・Gradleビルドは再実行していません。
- CI YAML・実行条件とbootJar生成条件72パターン・shell構文を確認しました。既存のSonarQube/reviewdog/依存検査ジョブは維持し、SpotBugsの既存指摘5件も隠していません。
- GitLab Runner上でのアーティファクト受け渡しを含む実パイプラインは未確認です。マージ後、保護mainの手動パイプラインで`RUN_ZAP_BASELINE=true`を指定して確認してください。

#### NVDキャッシュ保存先修正の動作確認

- 2026-10-23、設定上は`.dependency-check/`でも解析タスクがGradle User Home内へ保存する不一致を再現し、4タスクのProviderを明示設定して解消しました。CIからも同じ回帰試験を実行します。
- 隔離したプロジェクトで模擬NVDを使用し、標準保存先へのDB生成、実依存94件の解析、HTML/JSON/SARIF 2.1.0出力を確認しました。模擬API停止後もDBを再利用でき、厳格モードでは模擬CVEで失敗します。模擬データを通常の作業用キャッシュへは保存していません。
- 補助処理38テスト、Apache診断11ケース、CIの正常・異常9分岐、768通りの実行条件、YAML・Bash構文を確認しました。既存の他ジョブと実行条件は変更していません。アプリ本体の変更がないためJUnit全体は再実行していません。
- 実NVDの取得遅延・全件取得完了と、修正反映後のGitLabキャッシュ転送は未確認です。修正だけでタイムアウト解消や部分取得位置からの再開を保証しません。

### CIからのツール取得

2026-10-20にRunnerからの外部接続とジョブ内ダウンロードが許可されました。
LAN内GitLabは外部公開せず、解析ツール・依存物だけを公式配布元から取得する構成です。
Runnerは18.11.3、Linux/amd64を前提とします。

- 解析ジョブは既存の`eclipse-temurin:25-jdk`と`postgres:17`を使用し、APTからPostgreSQLクライアントとPythonを取得します。
- Gradle Wrapperで9.4.1を取得し、Plugin PortalとMaven Centralから依存物を解決します。Wrapperの配布ZIPは公式SHA-256で固定しています。
- 投稿ジョブは`ubuntu:24.04`にAPTでcurl・git・CA証明書を導入し、[公式reviewdog v0.21.1リリース](https://github.com/reviewdog/reviewdog/releases/tag/v0.21.1)からLinux x86_64配布物を取得します。
- reviewdogのアーカイブSHA-256は公式checksums.txtおよびGitHub Release APIのdigestと照合済みです。展開後のバイナリSHA-256も固定し、実行前に再検証します。
- バージョン・SHA-256はYAMLに固定済みです。`SPOTBUGS_CI_IMAGE`、`REVIEWDOG_CI_IMAGE`、`REVIEWDOG_SHA256`を管理画面で登録する必要はありません。以前の試行で登録した場合は、不要な上書き変数を削除してください。
- 新規ジョブは共有キャッシュを使いません。投稿ジョブのインストールはサブシェルでAPIトークンをunsetして実施し、取得ツールへトークンを渡しません。
- 社内CAはRunnerの既存`tls-ca-file`等で設定してください。投稿ジョブは`CI_SERVER_TLS_CA_FILE`がある場合にOSの信頼ストアへ登録します。証明書検証の無効化やCAの無認証取得は行いません。
- `CI_API_V4_URL`を`GITLAB_API`へ渡し、トークンを平文で送らないためHTTPS以外を拒否します。GitLabの外部URL設定とRunnerのCA設定を確認してください。

RunnerからDockerレジストリ、APT配布元、Gradle配布元、Plugin Portal、Maven Central、GitHub Releasesへの接続が必要です。
独自CIイメージの作成、依存キャッシュの手動搬入、reviewdogのホストへの事前インストールは不要です。
既存JUnit・Sonarのビルド環境もPluginの初回依存解決が必要ですが、オンライン取得できれば事前配置は不要です。
テスト処理は変更せず、MR pipelineでもJUnitがマージ判定から外れないよう実行ルールのみ明示しています。

### トークンとMRディスカッション

1. 対象プロジェクトだけのProject Access Tokenを作成します。ロールはまずDeveloper、スコープは`api`とし、有効期限を設定してください。導入先GitLabの権限設定で投稿・解決が可能なことを検証し、不要にMaintainerへ拡張しないでください。
2. Settings > CI/CD > Variablesへ`REVIEWDOG_GITLAB_API_TOKEN`を登録します。Visibilityは**Masked and hidden**、Environment scopeは必ず**`reviewdog-mr`**にします。既定の`*`では解析ジョブにもトークンが届くため不適切です。GitLabの契約・設定で環境スコープが利用できない場合は、この分離を安全境界とみなさず、保護された別プロジェクトの投稿処理へ移してください。
3. Protectedを付ける場合は、導入先GitLabのバージョンとMRの保護変数利用条件を確認してください。一般に同一プロジェクトの保護されたsource/targetブランチ、実行者の権限、MRから保護リソースへアクセスする設定が関係します。未保護ブランチやforkのMRへ自動的に公開しないでください。
4. マージチェックの**Pipeline must succeed**と**All discussions must be resolved**を有効にしてください。

投稿ジョブは環境`reviewdog-mr`を使い、Gradle、プロジェクトスクリプト、共有キャッシュを実行・利用しません。reviewdogのバージョンとバイナリSHA-256を毎回確認します。
`gitlab-mr-discussion`、`sarif`、固定ツール名`SpotBugs`、`added`フィルター、`fail-on-error`を指定し、MRの差分基点から現在のコミットまでの追加行を対象にします。
0.21.1の単発入力モードではツール名の関連付けと指摘0件時のFlushが不足し、自動解決が成立しないことを実バイナリで確認しました。
そのため、CI内で固定内容の一時runner設定を生成し、`SpotBugs` runnerから投稿用SARIFを`cat`で読む公式project runner方式を使います。
任意のリポジトリ内reviewdog設定やビルドコマンドは実行しません。`format: sarif`が入力形式、runner名がツール名に対応します。
修正後も同じMR・ツール名で再実行し、reviewdogが以前の自分のディスカッションと照合して解決します。**指摘0件でも投稿ジョブを省略しません。** 新しい変更行の指摘があれば投稿後にジョブが失敗します。解析失敗時は投稿ジョブへ進まず、未検証の結果で過去の指摘を解決しません。

ただし、MRから変更可能なCI設定自体に秘密変数を持たせる以上、ジョブ分離だけでは悪意のあるMRからトークンを保護できません。共通の保護CIテンプレートは現在確認できていません。
本番運用では投稿定義を保護された管理用プロジェクトのテンプレートやPipeline execution policyへ移し、非信頼MRを秘密情報付きで実行しない運用を必須としてください。環境スコープ・Masked設定はログ漏えい対策や誤配布防止であり、改変されたCIへの防御ではありません。

### MR静的解析の確認手順

- `./gradlew --no-daemon help`、`spotbugsMain`、`spotbugsMr`を実行します。DBが必要な場合は先に`flywayMigrateOnly generateJooq`を実施します。
- `python3 -B -m unittest discover -s tools/ci -p 'test_*.py'`でSARIFのパス解決・失敗判定・空の結果を検証します。
- MR専用タスクの後に正規化スクリプトを実行し、生成物の`version`が`2.1.0`、ファイルURIが`src/main/java/...`であることを確認してください。
- 配置済みreviewdogを使い、同じSARIFを`-f=sarif -name=SpotBugs -reporter=local`へ入力するとAPIへ書き込まずに読み取りを確認できます。
- `REVIEWDOG_BINARY=/path/to/reviewdog python3 -B tools/ci/reviewdog_smoke.py`で、実reviewdogとloopback限定の模擬GitLab APIによる投稿・差分フィルター・重複抑止・自動解決を検証できます。gitとPythonが必要です。実トークンは不要で、Git履歴も一時テストリポジトリ内だけに作成します。
- 検証用MRで追加行に検出対象を作り、SARIF保存、変更行へのコメント、投稿後の失敗を確認します。修正して再実行し、自動解決と成功を確認してください。
- エンジン起動失敗・クラス不足では解析ジョブが失敗し、投稿ジョブが起動しないことも確認してください。mainや通常のbranch pipelineでは新規2ジョブが起動しないことを確認します。

### 検証結果（2026-10-20）

- オンライン取得許可後、一時init scriptなしの通常の依存解決でGradle 9.4.1 / Java 25の構成と本番SpotBugs解析に成功しました。従来のオフラインメタデータ不足は解消しています。
- 空のGradleユーザーホームでもWrapperのSHA-256検証とPluginの初回取得を含む`help`が成功しました。
- 本番クラスでは74件を検出し、終了値`1`でもMR用タスクがSARIFを保存して成功すること、全74件のソースパスを正規化できることを確認しました。既存指摘の修正は本変更に含みません。通常の`check`では既存指摘も失敗対象です。
- 一時サンプルで問題なし時の成功、問題あり時のMR用タスクの成功と通常タスクの失敗、JVM起動失敗時の古いSARIFの排除を確認しました。
- 共有作業ツリーのビルド出力と競合しない検証用コピー・一時DBで、37マイグレーション、jOOQ生成、JUnit 376件（失敗0、スキップ2）、`bootJar`、`spotbugsMr`が成功しました。スキップは明示有効化する実MQTT試験とWindows固有の短い一時パス試験です。
- SARIF補助処理13テスト、YAML解析、新規ジョブのshell構文確認が成功しました。既存JUnitの実行コマンドとSonar設定は維持しています。
- 投稿ジョブの実インストール手順をUbuntu上で実行し、公式配布物の取得、アーカイブ・バイナリSHA-256照合、既存社内CAの登録に成功しました。
- 実reviewdogで本番SARIFのlocal reporter読み込みに成功しました。さらに模擬GitLab APIによる5テストで追加行への投稿、重複抑止、変更行以外の除外、0件時の自動解決、投稿失敗時に既存指摘を解決しないことを確認しました。
- 模擬APIは127.0.0.1のみで待ち受け、ダミートークンだけを使います。実LAN GitLabへの投稿試験ではありません。

### ユーザー側の残設定

| 残作業 | 必要な対応 |
| --- | --- |
| Project Access Token | Developer・apiスコープ・有効期限付きで作成し、`REVIEWDOG_GITLAB_API_TOKEN`をMasked and hidden、環境スコープ`reviewdog-mr`で登録 |
| GitLabのHTTPSと社内CA | `CI_API_V4_URL`がHTTPSとなる外部URL設定を確認。社内CAはRunnerの既存方式で配布し、`CI_SERVER_TLS_CA_FILE`としてジョブへ渡す |
| 秘密情報を扱うCI定義の保護 | MRから上書きできない外部CI設定またはPipeline execution policy等を使用。保護テンプレートをMR内YAMLからincludeするだけでは不十分 |
| マージチェック | Pipeline must succeed、All discussions must be resolvedを有効化 |
| 認証付き実環境確認 | GitLab CI Lint、検証用MRでの投稿・修正後の解決、既存Sonar解析を実行 |

CI LintへはTLS検証付きで匿名接続を試しましたが、プロジェクトAPIが`404 Project Not Found`となったため未完了です。
実MRとSonarQubeの書き込み試験に使う認証情報は登録・取得していません。
手動搬入・独自イメージの登録は不要になりましたが、上記の秘密情報・認証境界の準備前にmainへマージしないでください。
`build/`は生成物のままで、Git管理除外を解除する必要はありません。

## テスト時の注意

Mockito inline mock maker の自己 attach 警告を避けるため、`test` タスクでは Mockito を Java agent として明示的に読み込みます。
また、bootstrap classpath 追加時の CDS 警告を避けるため、テスト JVM には `-Xshare:off` を指定しています。

一部のテストや `generateJooq` は PostgreSQL 接続を前提とします。
DB が起動していない場合や接続情報が合っていない場合、テスト前段で失敗します。

## Tomcat・PostgreSQL JDBCのセキュリティ更新

- `gradle.properties`の`tomcatVersion=10.1.61`、`postgresqlVersion=42.7.14`をSpring Bootの依存管理へ渡しています。
  Spring Boot自体やServlet APIのメジャーバージョンは変更していません。
- Tomcatのcore・el・websocketを同じ10.1系パッチ版へ揃えます。
  JDBCはアプリ、テスト、`flywayMigration`、`jooqGenerator`で共通の版を使用します。
- 選定根拠は[Tomcat 10のセキュリティ情報](https://tomcat.apache.org/security-10.html)と
  [pgJDBC 42.7.14のリリース情報](https://github.com/pgjdbc/pgjdbc/releases/tag/REL42.7.14)です。
  Bootの更新時には、この依存バージョン上書きが引き続き必要か再確認してください。
- `TomcatJdbcSecurityIntegrationTest`は実HTTP、multipartパート数制限、
  両ステータスWebSocketの初期配信・チケット再利用拒否・読み取り専用制約を確認します。検証専用DBを使用してください。
- 過剰なmultipartパート数はTomcatで拒否されます。現行の共通例外処理ではこの解析例外を500で返すため、
  回帰テストは応答値だけでなく原因がパート数超過であることも確認します。
  通常のアップロード制限やエラー応答の設計は今回変更していません。
- 同テストのJDBC検証は、公開APIの`ByteStreamWriter`が宣言した長さより少ないデータを書いた場合のゼロ埋めと、
  通常の大きな値の往復を確認します。過去の送信バッファを保存する
  [CVE-2026-107315](https://github.com/pgjdbc/pgjdbc/security/advisories/GHSA-f64h-wr5q-3qf3)の回帰テストであり、
  アプリでの悪用成立を示すものではありません。
- 検証後も、CIでDependency-Checkを再実行し、配備済みJARを入れ替えてください。
  条件付きのHTTP/2・TLS・GSS等の全脆弱性を、この回帰テストだけで検証したとは扱いません。

## Swagger UI・Commons Lang・Agent認証・OCRのセキュリティ対応

- `swaggerUiVersion`はWebJar内の配信資産を更新し、Springdoc自体やAPI公開・認証設定は維持します。
  Swagger UI 5.34.0の配布物にはDOMPurify 3.4.16が同梱されており、旧版のJSを別途残す構成にはしません。
  [Swagger UI公式リリース](https://github.com/swagger-api/swagger-ui/releases/tag/v5.34.0)と
  [同版のロックファイル](https://github.com/swagger-api/swagger-ui/blob/v5.34.0/package-lock.json)を参照してください。
- `commonsLangVersion=3.18.0`は、長いクラス名の処理で再帰が深くなるCVE-2025-48924の修正版です。
  アプリ・テストの依存管理に反映し、Gradleプラグイン側にも下限の制約を追加します。
  既により新しい修正版を使用するビルドツールの依存関係は下げません。
  [Apacheの修正内容](https://github.com/apache/commons-lang/commit/b424803)を参照してください。
- Agentの登録コード・現在と直前の更新トークンは、ロック取得後も`MessageDigest.isEqual`で比較します。
  DB検索、署名確認、ロック順序、世代・失効確認、再送時のキャッシュ応答は変更していません。
- OCR起動先は`tesseract`、`tesseract.exe`、または同名の実行ファイルの絶対パスに限定します。
  シェル名、コマンド列、相対実行パス、独自ラッパーはサポートしません。
  インストール先とサービスの`PATH`は管理者だけが変更できる場所にしてください。
  ファイル名の検証だけで実行ファイルの真正性や所有権を保証するものではありません。
- 言語設定は`jpn+eng`、`jpn_vert`、`script/Latin`等の言語名の組合せに限定します。
  入力画像は絶対パスの通常ファイルとして渡し、シンボリックリンクを拒否します。
  引数をシェルで解釈・分割せず、タイムアウト、出力制限、プロセス終了処理は維持します。
  Tesseractと日本語・英語データを用意し、`TESSERACT_INTEGRATION_TEST=true`でJUnitを実行すると、
  実行ファイルを使う画像読み取り試験も有効になります。未指定時はその1件だけをスキップします。
- FindSecBugsのOCR指摘は、この検証済み起動メソッドだけを理由付きで除外します。
  HTTPから実行コマンドを指定する経路はなく、起動引数の検証と回帰試験を併用します。
  他のプロセス起動箇所や認証の指摘を包括的に除外する設定ではありません。
- DOMPurifyには3.4.17も公開されていますが、公式Swagger UI 5.34.0の同梱版は3.4.16です。
  添付レポートの修正範囲と「DOMPurify自体が最新版」は区別してください。
  新しい版で修正された`IN_PLACE`・フック・Shadow DOM等の経路はSwaggerの通常の文字列サニタイズとは異なります。
  同梱版の追従状況は[DOMPurify公式リリース](https://github.com/cure53/DOMPurify/releases/tag/3.4.17)と合わせて確認してください。

## 開発時の注意

### DBアクセスの統一メモ

- 結合種別を省略せず、内部結合は`.innerJoin()`、外部結合は`.leftJoin()`等で記載します。
  共通の`JooqConfig`により、実行SQLでも`INNER JOIN`を省略しません。既存の外部結合・結合条件は維持します。
- アプリのDBアクセスにはjOOQを使用します。JPAへの置換ではなく、既存のjOOQ生成定義へ統一しています。
  手書きのSQL実行、JdbcTemplate、テーブル名・カラム名の手書き定義は業務実装で使用しません。
- Controller・ServiceにあったDB照会はRepositoryへ分離し、認可確認とトランザクションの範囲は維持しています。
  Flywayの移行SQLと、テストの独立した準備・期待値照合用SQLは残します。SQLの説明をメモ・コメントへ記載しても構いません。
- 在庫更新は在庫行の排他ロックと品目行の共有ロックを維持します。混在するロックモードは、
  型付きのCTEと外側のSELECTで表現し、更新対象・公開条件・重複操作の予約と応答保存は変更しません。
- Agentの状態保存は世代・採取時刻を照合した条件付きUPSERTを維持し、古い計測値で子スナップショットを上書きしません。
  認証回数・APIキーの時間窓内回数も、同時要求を取りこぼさないDBの原子更新を使用します。
- APIキーの失効・最終利用判定はDBの実時刻、時間窓はトランザクション時刻です。
  APIバージョンの非推奨日時は日本時間として解釈し、Unix秒の丸めもDBで従来どおり行います。
- `JooqDataAccessPolicyTest`と実DBの回帰テストで、アクセス方式・ロック・同時更新・日時変換を確認します。

- Flyway マイグレーションを変更した場合は、DB へ適用してから `generateJooq` を実行してください。
- jOOQ 生成コードは `build/generated/jooq` に出力されます。
- `compileJava` は `generateJooq` に依存しています。
- 論理削除は専用削除 API ではなく、ステータス更新で扱います。
- 機密情報は `application.yaml` に直接記載せず、環境変数または secret 管理を使用してください。
- `git push` や `git commit` は作業方針上、明示依頼がある場合のみ実行します。

## Agent登録・専用認証（2026/09/23）

端末マスタの保存後、管理者が `POST /api/master/device/{deviceId}/agent/registration-code` でコードを発行します。
Agentは `POST /api/v1/agent/auth/challenge` のnonceへEd25519 JWSで署名し、`POST /api/v1/agent/register` へコードをJSON本文で送ります。
資格情報の更新は `POST /api/v1/agent/auth/refresh`、認証確認は `GET /api/v1/agent/me` です。
管理画面から登録状態・世代・最終認証確認を確認し、理由を付けて無効化・再登録できます。

V22マイグレーションと専用の `AGENT_RESPONSE_ENCRYPTION_KEY` の設定が必要です。HTTPSを終端し、平文待受を直接公開しない構成で運用します。
既存の実行要求には登録世代を補完しません。必要な実行は対象端末の登録後、管理者が新しい実行IDで作成してください。
Agent向けMQTTの安全な有効化にはブローカー選定と専用認証・ACL・接続失効・登録世代別配信の実装が必要です。

詳細はドキュメントリポジトリの `docs/02_SystemDesign/認証/0005_agent_authentication.md` と `docs/05_API_Design/API/0041_agent_registration_authentication.md` を参照してください。
