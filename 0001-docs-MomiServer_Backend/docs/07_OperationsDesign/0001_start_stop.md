# 起動・停止設計

## 起動

| 項目 | 内容 |
| --- | --- |
| 起動方式 | Spring Bootアプリケーションとして起動 |
| 既定ポート | 8080 |
| 起動前提 | PostgreSQL接続、マイグレーション実行可能、JWT秘密鍵設定 |

## 起動時処理

1. アプリケーション設定を読み込む。
2. PostgreSQLへ接続する。
3. FlywayでDBマイグレーションを確認する。
4. Web API、認証、Actuator、OpenAPIを公開する。

## WindowsでAgent登録を検証する際の起動手順

### DBの事前確認

この開発環境では既存のPodmanコンテナ`zunda-db`を使用する。新規DB作成・ボリューム削除は不要。

```powershell
podman machine list
podman ps -a
```

Podman machineが停止中なら起動し、`zunda-db`が停止中の場合だけ起動する。

```powershell
podman machine start
podman start zunda-db
podman exec zunda-db pg_isready
Test-NetConnection localhost -Port 5432
```

machineが既に起動している場合は`podman machine start`を省略する。DBが`accepting connections`、WindowsからのTCP接続が成功することを確認する。
`generateJooq`はビルド中に実DBへ接続するため、バックエンド起動前からPostgreSQLが必要となる。2026年9月23日の接続拒否は、停止していた`zunda-db`の起動によりlocalhost:5432への接続回復を確認した。

### 設定と起動

1. [設定管理設計](0002_configuration_management.md)に従い、DB接続設定とAgent専用暗号化鍵をバックエンド実行プロセスへ渡す。Gradle生成処理にも同じDB接続設定を渡す。
2. Agent認証のDB定義には`V22__create_agent_authentication.sql`が必要。既存DBのバックアップ・対象確認を行い、[DBマイグレーション運用](0003_database_migration.md)に従って適用する。
3. IDEで起動するか、下記のGradle手順を実行する。実行中のバックエンドがある場合は二重起動を避ける。

DB接続環境変数と鍵を設定済みのPowerShellから、必要なマイグレーションを生成処理より先に実行する例:

```powershell
cd D:\6000.PROJECTS\MomiServer\smarthomewebserverbackend
.\gradlew.bat flywayMigrateOnly
.\gradlew.bat bootRun --args="--mqtt.enabled=false"
```

各コマンドの成功を確認して次へ進む。`flywayMigrateOnly`は対象DBを更新するため、別環境・本番DBを誤指定しない。HTTP登録のみの検証例としてMQTTを無効にしている。
IntelliJの場合はアプリケーションのプログラム引数へ`--mqtt.enabled=false`を設定できる。通常のMQTT機能も検証する場合は[設定管理設計](0002_configuration_management.md)に従ってブローカー接続を構成する。

### 起動後の確認と終了

- 起動ログでアプリケーションの起動完了と8080番ポートを確認する。DB接続やFlywayの例外が残っている場合は先に解消する。
- 管理画面からテスト用端末の登録コードを発行し、Agentのregister・status・refreshで確認する。CLI操作はAgent設計リポジトリの`docs/07_OperationsDesign/010_registration_test_guide.md`を参照する。
- 終了時はIDEの停止、またはbootRunのコンソールでCtrl+Cを使用する。DBは他作業でも使用されるため、一律に停止する手順にはしない。
- この手順書の更新作業では起動・DB更新を実行していない。利用者環境で報告済みなのはAgent登録成功までであり、MQTT連携の完成を意味しない。

## 停止

OSまたは実行基盤の標準的なプロセス停止に従う。

## 起動時のバッチ設定

| 項目 | 内容 |
| --- | --- |
| Batch Job | `application.yaml` の `spring.batch.job.enabled` は `${BATCH_JOB_ENABLED:false}` |

2026/09/20にコメントと既定値の不整合を解消した。現行ソースに起動対象となるSpring BatchのJob定義は確認されなかった。
今後Jobを追加しても暗黙に起動実行しない。必要な環境だけ `BATCH_JOB_ENABLED=true` を指定する。
この設定はSpring Batchの起動時Job実行に対するものであり、`@Scheduled` による実行要求の展開・再送・監視は停止しない。
`BatchExecutionScheduler` は `ApplicationReadyEvent` でも当日・翌日の実行要求を展開するため、この起動時処理は引き続き動作する。
