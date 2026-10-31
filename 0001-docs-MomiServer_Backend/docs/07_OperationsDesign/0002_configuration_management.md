# 設定管理設計

## 環境変数で管理する項目

| 項目 | 用途 |
| --- | --- |
| SERVER_PORT | 待受ポート |
| DB_HOST | DBホスト |
| DB_PORT | DBポート |
| DB_NAME | DB名 |
| DB_USERNAME | DBユーザー |
| DB_PASSWORD | DBパスワード |
| JWT_SECRET | JWT署名用秘密鍵 |
| AGENT_RESPONSE_ENCRYPTION_KEY | Agent登録・更新の短期復旧応答を暗号化する専用鍵 |
| MQTT_BROKER_URL | MQTT Broker URL |
| MQTT_CLIENT_ID | MQTT Client ID |
| MQTT_USERNAME | MQTTユーザー |
| MQTT_PASSWORD | MQTTパスワード |
| DISCORD_NOTIFICATION_ENABLED | Discord通知全体の有効 / 無効 |
| DISCORD_WEBHOOK_URL | Discord Webhook通知先 |
| SECURITY_EVENT_DISCORD_NOTIFICATION_ENABLED | 不正アクセス監査イベントのDiscord通知有効 / 無効 |
| SECURITY_EVENT_DISCORD_MINIMUM_SEVERITY | Discord通知対象とする最低重要度 |
| SECURITY_EVENT_DISCORD_AGGREGATION_WINDOW_SECONDS | 同種セキュリティイベントを集約通知する時間窓 |
| APP_CORS_ALLOWED_ORIGINS | CORS許可オリジン |

## APIアクセスログ設定

| 設定キー | 環境変数 | 既定値 | 用途 |
| --- | --- | --- | --- |
| app.api-access-log.enabled | API_ACCESS_LOG_ENABLED | true | APIアクセスログの記録可否 |
| app.api-access-log.retention-days | API_ACCESS_LOG_RETENTION_DAYS | 30 | 保持日数 |
| app.api-access-log.cleanup-cron | API_ACCESS_LOG_CLEANUP_CRON | `0 15 2 * * *` | 期限切れ削除の実行Cron |

記録可否はフィルターの適用を制御する。falseにしても既存ログの検索・詳細取得・定期削除は停止しない。
保持日数が1未満の場合、削除処理では1日に補正する。0を無期限保持の指定として使用しない。
削除対象は実行時刻から保持日数を引いた日時より前のログで、境界と同じ日時のログは含まない。
削除スケジュールに専用タイムゾーン指定はないため、運用環境のスケジューラのタイムゾーンを確認する。
記録対象・機密情報の除外は[APIアクセスログ設計](../10_LoggingAndMonitoringDesign/0004_api_access_log.md)を参照する。

## 車検証取込設定

| 設定キー | 環境変数 | 既定値 | 用途 |
| --- | --- | --- | --- |
| `app.vehicle-import.root` | `VEHICLE_IMPORT_ROOT` | OS一時領域配下 | 確認中原本の一時保存先 |
| `app.vehicle-inspection-notification.enabled` | `VEHICLE_INSPECTION_NOTIFICATION_ENABLED` | `true` | 車検期限の日次・起動時通知を有効化 |
| `app.vehicle-inspection-notification.cron` | `VEHICLE_INSPECTION_NOTIFICATION_CRON` | `0 0 9 * * *` | 日本時間09:00の車検期限確認 |
| `app.vehicle-import.max-bytes` | `VEHICLE_IMPORT_MAX_BYTES` | `10485760` | 原本ファイル最大バイト数 |
| `app.vehicle-import.max-image-pixels` | `VEHICLE_IMPORT_MAX_IMAGE_PIXELS` | `40000000` | 画像の最大展開画素数 |
| `app.vehicle-import.max-pdf-pages` | `VEHICLE_IMPORT_MAX_PDF_PAGES` | `10` | PDF最大ページ数 |
| `app.vehicle-import.review-lifetime` | `VEHICLE_IMPORT_REVIEW_LIFETIME` | `PT24H` | 確認待ち保持期間と孤立ファイル削除基準 |
| `app.vehicle-import.cleanup-cron` | `VEHICLE_IMPORT_CLEANUP_CRON` | `0 15 * * * *` | 期限切れ・孤立原本の削除Cron |
| `app.document-extraction.tesseract-command` | `TESSERACT_COMMAND` | `tesseract` | Tesseract CLI実行ファイル |
| `app.document-extraction.language` | `TESSERACT_LANGUAGE` | `jpn+eng` | OCR言語データ |
| `app.document-extraction.timeout` | `TESSERACT_TIMEOUT` | `PT30S` | 1画像当たりのOCR上限時間 |
| `app.document-extraction.document-timeout` | `DOCUMENT_EXTRACTION_TIMEOUT` | `PT330S` | 1文書全体の解析上限時間 |
| `app.document-extraction.rendered-dpi` | `TESSERACT_RENDERED_DPI` | `300` | スキャンPDF画像化DPI |
| `app.document-extraction.max-rendered-pixels` | `DOCUMENT_MAX_RENDERED_PIXELS` | `40000000` | PDFページの最大展開画素数 |
| `app.document-extraction.max-extracted-characters` | `DOCUMENT_MAX_EXTRACTED_CHARACTERS` | `500000` | 文書から保持する最大抽出文字数 |

本番の`VEHICLE_IMPORT_ROOT`はアプリケーション専用の空ディレクトリとし、親ディレクトリを事前作成する。バックエンドはルートを所有者のみ`700`、原本を所有者のみ`600`相当へ制限し、適用できないファイルシステムでは取込を拒否する。シンボリックリンク、共有ディレクトリおよび他用途との共用は認めない。画像・スキャンPDFを取り込む環境にはTesseract本体と`jpn`、`eng`言語データを導入する。

## 機密情報の管理方針

- DBパスワード、JWT秘密鍵、通知Webhook URLは環境変数で管理する。
- 本番環境では設定ファイルへ機密情報を直接記載しない。
- JWT秘密鍵は十分な長さのランダム文字列を設定する。

## systemd 環境変数ファイル

本番環境で systemd によりバックエンドを常駐起動する場合、秘匿値は systemd の `EnvironmentFile` で読み込む。

リポジトリには実値を含めず、サンプルとして `tools/systemd/smarthome-backend.secrets.env.example` のみを管理する。

| 項目 | 方針 |
| --- | --- |
| 本番配置先 | `/etc/smarthome/smarthome-backend.secrets.env` |
| 所有者 | アプリケーション実行ユーザーまたは `root` |
| 権限 | `600` を基本とし、実行ユーザーのみ読取可能にする |
| Git管理 | 実値ファイルは管理対象外。サンプルファイルのみ管理する |
| 読み込み方法 | systemd unit の `EnvironmentFile=/etc/smarthome/smarthome-backend.secrets.env` で読み込む |

systemd unit 側では、アプリケーション起動前に秘匿値を環境変数として渡す。

```ini
[Service]
EnvironmentFile=/etc/smarthome/smarthome-backend.secrets.env
ExecStart=/usr/bin/java -jar /opt/smarthome/smarthomewebserverbackend.jar
```

`EnvironmentFile` は Spring Boot の `.env` 自動読み込みではなく、systemd が起動プロセスへ環境変数を渡す仕組みとして利用する。

## 秘匿値用ファイルで管理する項目

秘匿値用ファイルでは、漏えい時の影響が大きい値のみを管理する。

| 環境変数 | 用途 |
| --- | --- |
| `DB_PASSWORD` | DB接続パスワード |
| `ADMIN_PASSWORD` | 初期管理者パスワード |
| `JWT_SECRET` | JWT署名用秘密鍵 |
| `AGENT_RESPONSE_ENCRYPTION_KEY` | Agent認証用の32バイト暗号化鍵（標準Base64） |
| `GATEWAY_AGENT_API_KEYS` | 廃止した旧設定。現行アプリケーションでは読み込まない。Agent専用認証を使用する |
| `GATEWAY_IOT_API_KEYS` | IoT機器向けAPIキー |
| `MQTT_USERNAME` | MQTT認証ユーザー |
| `MQTT_PASSWORD` | MQTT認証パスワード |
| `DISCORD_WEBHOOK_URL` | Discord Webhook URL |

DBホスト、ポート、機能ON/OFFなどの非秘匿設定は `application.yaml` の既定値または別の環境設定として管理し、秘匿値ファイルへ集約しすぎない。

## API統合管理の設定と運用

| 設定 | 既定値 | 内容 |
| --- | --- | --- |
| `GATEWAY_OPENAPI_PUBLIC_ENABLED` | `true` | `true` ならOpenAPI JSON・Swagger UIを公開。`false` ならADMIN権限を要求 |
| `GATEWAY_DEFAULT_API_VERSION` | `v1` | 既定値の設定。DBの公開状態判定や経路切替には使用しない |

公開を制限する場合、`/v3/api-docs`、`/v3/api-docs.yaml`、Swagger UIへの要求に管理ユーザーJWTが必要となる。
通常のブラウザ直接遷移ではAuthorizationヘッダーを付けられないため、Swagger UIの画面利用にはJWTを付与できる経路が
別途必要である。

Agent・IoTの公開状態は `m_api_versions` で管理し、Phase 1は `v1` のみを実装する。
状態変更はマイグレーション等の運用変更として実施し、反映後に対象経路の応答を確認する。
`deprecated` にする場合は `deprecated_dt` をAsia/Tokyoの日時で設定する。
未設定なら `updated_dt` が警告日時に使われる。
`retired` では既存クライアントがHTTP 410を受けるため、切替計画を先に確定する。
DB参照不可時は認証処理へ進まずHTTP 503を返す。

`m_external_clients` のクライアント別許可バージョンとAgent用APIキーハッシュは現行の認証判定に使用しない。Agent専用認証を維持し、複数バージョンやクライアント別許可が必要になった時点で再設計する。

## Discord通知設定

Discord通知はWebhookを利用し、Bot Tokenは初期実装では使用しない。

Webhook URLは通知先チャンネルへの投稿権限を持つため、パスワードやJWT秘密鍵と同様に機密情報として扱う。

| 設定キー | 環境変数 | 既定値 | 内容 |
| --- | --- | --- | --- |
| `notification.discord.enabled` | `DISCORD_NOTIFICATION_ENABLED` | `false` | Discord通知全体の有効 / 無効 |
| `notification.discord.webhook-url` | `DISCORD_WEBHOOK_URL` | 空文字 | Discord Webhook URL。必要な環境で設定する |
| `notification.discord.security-events.enabled` | `SECURITY_EVENT_DISCORD_NOTIFICATION_ENABLED` | `false` | 不正アクセス監査イベント通知の有効 / 無効 |
| `notification.discord.security-events.minimum-severity` | `SECURITY_EVENT_DISCORD_MINIMUM_SEVERITY` | `warning` | 通知対象とする最低重要度 |
| `notification.discord.security-events.aggregation-window-seconds` | `SECURITY_EVENT_DISCORD_AGGREGATION_WINDOW_SECONDS` | `300` | 同種イベントを集約通知する時間窓 |

Discord側から確認済み化や対応状態更新を行う必要が出た場合にのみ、Bot導入を再検討する。

上表は2026/09/20の修正後の既定値。固定Webhookはソースから除去した。
Git履歴の値は失効しないため、旧Webhookが有効な場合は運用者がDiscord側で失効・再発行する。今回外部サービスの変更は行っていない。
DBに保存済みの通知共通設定は別途確認する。設定ファイルの既定値変更だけでは保存済み値を変更しない。
バックエンドCIでは両通知フラグを `false`、Webhook URLを空文字に上書きしている。

## CORS設定

フロントエンドからのAPI呼び出しを許可するオリジンは、`APP_CORS_ALLOWED_ORIGINS` で管理する。

| 設定キー | 環境変数 | 既定値 | 内容 |
| --- | --- | --- | --- |
| `app.cors.allowed-origins` | `APP_CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | フロントエンドの許可オリジン |

複数オリジンを許可する場合は、カンマ区切りで指定する。

## Podman / PostgreSQL運用補助

PostgreSQLのローカル運用および本番移行検討用に、Podman関連の補助ファイルを管理する。

| ファイル | 用途 |
| --- | --- |
| `tools/podman/README.md` | DockerからPodmanへの移行方針、実施内容、検証結果 |
| `tools/podman/compose.podman.yml` | 開発環境向けPodman Compose設定 |
| `tools/podman/quadlet/zunda-db.container` | systemd QuadletによるPostgreSQLコンテナ管理設定 |
| `tools/podman/quadlet/zunda-db.env.example` | PostgreSQLコンテナ用環境変数サンプル |

開発環境ではPodman Composeを利用できる。2026/10/27の追加合意では、本番のPostgreSQL・MQTTは既存Dockerを
継続利用する方針へ確定した。Quadletは開発・構成検討用の資産であり、本番で採用済みとは扱わない。
FE・BEはホスト上で直接稼働させる。最新の導入条件は[本番導入・受入設計](0005_production_acceptance.md)を参照する。

Podman machine環境ではbridgeネットワークの挙動が環境依存になるため、公開ポート、ネットワーク、SELinuxラベル、rootless実行時のUID/GIDを本番環境で再確認する。

## Agent専用認証

`AGENT_RESPONSE_ENCRYPTION_KEY`に32バイトの暗号学的乱数を標準Base64化して設定する。管理ユーザーJWTの鍵とは分け、複数バックエンド間で同じ鍵を安全に配布する。未設定・不正設定の場合、登録コード発行・nonce・登録・更新は503となる。
この鍵は短期復旧応答の暗号化用。DBバックアップと別経路で保護・バックアップする。鍵を変更すると古い復旧応答は復号できなくなるが、保存済みBearerのSHA-256照合には影響しない。更新中・登録中の端末の復旧期限を考慮して変更し、鍵漏えい時は該当資格情報の失効・再登録も実施する。

| 環境変数 | 初期値 | 用途 |
| --- | --- | --- |
| AGENT_ACCESS_MINUTES | 15 | アクセストークン期限。6分以上 |
| AGENT_REFRESH_DAYS | 30 | 更新トークン期限 |
| AGENT_REGISTRATION_MINUTES | 30 | 登録コード期限 |
| AGENT_REGISTRATION_RECOVERY_HOURS | 24 | 登録応答の復旧期間 |
| AGENT_REFRESH_RECOVERY_MINUTES | 5 | 更新応答の復旧期間 |
| AGENT_AUTH_REQUESTS_PER_MINUTE | 60 | 接続元IP単位の認証要求回数 |

HTTPS終端を構成し、平文HTTP待受を直接外部へ公開しない。登録・更新本文、Authorization、proofをプロキシのログにも残さない。
MQTTブローカーはEclipse Mosquittoを採用する（2026年9月23日決定）。Dynamic SecurityによるAgent専用連携を実装した。既定は無効のため、[Agentバッチ実行・Mosquitto運用](0004_agent_batch_runtime.md)に従いプラグイン・管理接続・配信用ユーザーを構成して有効化する。

V23以降、この専用鍵はMQTT資格情報の暗号化にも使用する。鍵だけを変更すると既存MQTT資格情報を復号できなくなるため、変更時は[MQTT資格情報テーブル](../04_DatabaseDesign/Tables/t_agent_mqtt_credentials.md)の鍵変更に関する制約を確認する。

## Agent開発テスト時のバックエンド設定

2026年9月23日時点の`application.yaml`、`build.gradle`、Agent認証実装に基づく。以下はローカル環境向けの設定手順であり、本番環境の変更を実施した記録ではない。

| 項目 | ローカルでの設定・確認 | 注意 |
| --- | --- | --- |
| SERVER_PORT | 8080 | Agentの接続先は`http://localhost:8080` |
| DB_HOST / DB_PORT | localhost / 5432 | Windowsから接続できるPostgreSQLのホストとポート |
| DB_NAME / DB_USERNAME | zunda / zunda_user（既定値） | 実際のDB構成と一致させる |
| DB_PASSWORD | 対象DBのパスワード | 実値を文書やGitに記録しない |
| AGENT_RESPONSE_ENCRYPTION_KEY | 上記要件の鍵を設定 | JWT_SECRET・MQTT_PASSWORDとは別。Agent側に設定しない |
| APP_CORS_ALLOWED_ORIGINS | 管理画面の実際のオリジン | CLIにはブラウザーのCORS制限は適用されない |
| mqtt.enabled | HTTP登録だけの検証では必要に応じてfalse | AgentのMQTT専用連携の実装状況とは別の設定 |

### IntelliJから起動する場合

1. バックエンドを起動する実行構成を開き、環境変数へDB設定と`AGENT_RESPONSE_ENCRYPTION_KEY`を設定する。鍵は生成したBase64文字列をそのまま値にする。
2. `generateJooq`をIDEのGradle実行構成で起動する場合は、その構成にも同じDB接続先・資格情報を渡す。Spring Boot側の実行構成だけに設定しても、別のGradleプロセスには引き継がれない。
3. 設定を保存してバックエンドを再起動する。既に起動しているプロセスには環境変数の変更は反映されない。
4. 管理画面の端末マスタから登録コードを発行し、Agentで登録・status・refreshを確認する。

専用鍵の生成コマンドとAgent側操作は、Agent設計リポジトリの`docs/07_OperationsDesign/010_registration_test_guide.md`を参照する。鍵は一度生成して安全に保持し、再起動ごとに変更しない。
`.env`ファイルを置くだけで、現在のSpring Boot・Gradle構成が自動的に読み込むとは扱わない。実行プロセスへ環境変数として渡す。

### Gradleと実行時DB設定の違い

| 処理 | URL解決順 | ユーザー・パスワード |
| --- | --- | --- |
| generateJooq | JOOQ_DB_URL → DB_HOST / DB_PORT / DB_NAME | JOOQ_DB_USER・JOOQ_DB_PASSWORD → DB_USERNAME・DB_PASSWORD → 開発用既定値 |
| flywayMigrateOnly | FLYWAY_DB_URL → DB_URL → DB_HOST / DB_PORT / DB_NAME | FLYWAY_DB_USER・FLYWAY_DB_PASSWORD → DB_USERNAME・DB_PASSWORD → 開発用既定値 |
| Spring Boot実行時 | application.yamlではDB_HOST / DB_PORT / DB_NAME | DB_USERNAME・DB_PASSWORD |

`DB_URL`は上記マイグレーション専用タスクでは利用されるが、現在のjOOQ設定やapplication.yamlのDB接続先を一括変更する変数ではない。
起動時に正しくても生成時だけ別DBへ接続しないよう、通常は共通のDB_HOST / DB_PORT / DB_NAME / DB_USERNAME / DB_PASSWORDを揃える。

## バックエンドのMQTT設定とAgent連携の区別

| 設定 | 現在の既定値 | 用途 |
| --- | --- | --- |
| mqtt.enabled | true | 既存MQTT機能の有効設定。HTTPだけの検証では起動引数`--mqtt.enabled=false`で上書き可能 |
| MQTT_BROKER_URL | tcp://localhost:1883 | バックエンドからブローカーへの接続先 |
| MQTT_CLIENT_ID | smarthome-backend | バックエンド側Client IDの基準値。AgentのClient IDとは別 |
| MQTT_USERNAME / MQTT_PASSWORD | 空文字 | ブローカーがバックエンド接続に要求する資格情報 |
| mqtt.topics.telemetry / status | home/+/telemetry / home/+/status | 現行設定のIoTトピック |
| mqtt.topics.batch-command | smarthome/agents/{agentId}/batch/command | 現行のバッチ命令トピック設定。世代別配信の完成仕様ではない |

Mosquittoが認証を要求する場合は、バックエンド接続用ユーザーとACLに合わせて設定する。既存のローカルACLは`momi/#`であり、上表のトピックとは異なるため、資格情報の設定だけで送受信できるとは判断しない。
共通MQTT設定だけではAgent専用連携を有効化しない。追加したAGENT_MQTT_*環境変数、プラグイン準備、専用接続設定取得APIについては[Agentバッチ実行・Mosquitto運用](0004_agent_batch_runtime.md)を参照する。

## Agent認証設定の障害切り分け

| 表示・事象 | 確認箇所 | 対応 |
| --- | --- | --- |
| Agent認証の専用鍵が未設定です。 | 実行プロセスのAGENT_RESPONSE_ENCRYPTION_KEY | 未設定・Base64不正・復号後32バイト以外を修正して再起動 |
| Agent認証設定を確認してください。 | 認証期限・回数設定 | アクセス期限6分以上、その他の表中数値は1以上にする |
| generateJooqでConnection refused | PostgreSQL稼働と生成用DB設定 | [起動・停止設計](0001_start_stop.md)の事前確認を実施 |
| CLIでHTTP拒否 | Agentの起動オプション | ループバック宛ての開発時だけ`--allow-insecure-localhost`を毎回指定。バックエンドの鍵設定では解除されない |
| MQTT: 未構成 | Agent連携の実装状況 | 現段階の想定状態。登録コードやHTTPトークンの再発行では解消しない |
