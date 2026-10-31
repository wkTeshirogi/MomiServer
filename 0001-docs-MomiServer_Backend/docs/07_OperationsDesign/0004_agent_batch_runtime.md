# Agentバッチ実行・Mosquitto運用

## 概要

2026年9月23日実装。登録済みAgentを`run`で常駐させ、MQTT通知とHTTP状態照合からバッチを実行する。端末配置済みコマンドに加え、バッチ定義から添付したスクリプト・入力・設定を配布して実行できる。Agentからの成果物アップロード、詳細リソース監視、インストーラーは対象外。

## 対象

| 項目 | 内容 |
| --- | --- |
| DB | V22適用後、V23（MQTT資格情報・ハートビート・出力）、V24（パラメータスナップショット）を適用 |
| MQTT | Eclipse Mosquitto 2.0 Dynamic Security。既存IoT用の静的ACLとは構成を分けて確認 |
| Agent | Go製CLI。Windows/Linux/macOS。Windows/Linuxで動作検証、macOSはビルド確認 |
| 実行アカウント | Agentを起動したOSアカウント。資格情報・実行ファイル・作業ディレクトリへの権限が必要 |

## 運用手順

### 1. 開発用Mosquittoの準備

既存の`momi-mosquitto`は静的ACL構成のまま保持できる。最初は別のブローカーを18884番で用意し、既存IoT通信への影響を避けて確認する。以下はWindows＋Podman machineの開発例。通常環境ではTLSを使用する。

```powershell
podman volume create momi-agent-mqtt-config
podman run --rm -it --network host `
  -v momi-agent-mqtt-config:/mosquitto/config `
  --entrypoint mosquitto_ctrl eclipse-mosquitto:2.0 `
  dynsec init /mosquitto/config/dynamic-security.json agent-control
```

初期管理者のパスワードを対話入力する。初期化は新しいボリュームに対して一度だけ実行し、既存のDynamic Security設定を上書きしない。
次に開発用設定を作成する（ここで変更するのは上記専用ボリュームのみ）。

```powershell
$configCommand = 'printf "listener 18884\nallow_anonymous false\n' +
  'plugin /usr/lib/mosquitto_dynamic_security.so\n' +
  'plugin_opt_config_file /mosquitto/config/dynamic-security.json\n" ' +
  '> /mosquitto/config/mosquitto.conf; chown -R mosquitto:mosquitto /mosquitto/config'
podman run --rm --network host `
  -v momi-agent-mqtt-config:/mosquitto/config `
  --entrypoint sh eclipse-mosquitto:2.0 -c $configCommand
podman run -d --name momi-agent-mqtt --network host `
  -v momi-agent-mqtt-config:/mosquitto/config eclipse-mosquitto:2.0
```

hostネットワークの18884番はホストの待受になる。ローカル検証環境でのみ使用し、通常運用には証明書・鍵・CAを配置したTLS listenerを構成する。公式イメージ以外ではプラグインパスを確認する。

### 2. バックエンドの命令配信用ユーザー

管理用ユーザーをAgentや通常配信に流用しない。次の各コマンドはパスワードを引数にせず対話入力する。createClientでは新しい配信用ユーザーのパスワードも設定する。

```powershell
podman exec -it momi-agent-mqtt mosquitto_ctrl `
  -h 127.0.0.1 -p 18884 -u agent-control dynsec createRole agent-publisher
podman exec -it momi-agent-mqtt mosquitto_ctrl `
  -h 127.0.0.1 -p 18884 -u agent-control dynsec addRoleACL `
  agent-publisher publishClientSend 'smarthome/agents/+/batch/+' allow
podman exec -it momi-agent-mqtt mosquitto_ctrl `
  -h 127.0.0.1 -p 18884 -u agent-control dynsec createClient agent-publisher
podman exec -it momi-agent-mqtt mosquitto_ctrl `
  -h 127.0.0.1 -p 18884 -u agent-control dynsec addClientRole agent-publisher agent-publisher
```

Agentごとのユーザー・ロールはバックエンドが自動作成する。手動で共通パスワードをAgentへ配布しない。

### 3. バックエンド環境変数

既存のDB設定・AGENT_RESPONSE_ENCRYPTION_KEYは[設定管理](0002_configuration_management.md)に従う。新しい設定を追加して再起動する。

| 環境変数 | 開発例 | 意味 |
| --- | --- | --- |
| AGENT_MQTT_ENABLED | true | Agent用Dynamic Security同期を有効化。既定false |
| AGENT_MQTT_CONTROL_URL | tcp://127.0.0.1:18884 | バックエンドからの管理用接続。通常はssl:// |
| AGENT_MQTT_BROKER_URL | tcp://127.0.0.1:18884 | Agentへ配布する接続先。通常はAgentから到達可能なTLSホスト名 |
| AGENT_MQTT_CONTROL_USERNAME | agent-control | 専用のDynamic Security管理ユーザー |
| AGENT_MQTT_CONTROL_PASSWORD | 初期化時の値 | 秘密値。Git管理禁止 |
| AGENT_MQTT_CONTROL_CLIENT_ID | momiserver-dynsec | 管理接続ID。複数バックエンドでは重複を避ける |
| AGENT_MQTT_ALLOW_INSECURE_LOCALHOST | true | 開発用tcpをループバックだけに許可。通常はfalse |
| MQTT_BROKER_URL | tcp://127.0.0.1:18884 | 既存の命令配信用接続先 |
| MQTT_USERNAME | agent-publisher | 配信用ユーザー |
| MQTT_PASSWORD | 配信用に設定した値 | 秘密値 |

バッチ通知には`mqtt.enabled=true`も必要。TLSのCAはJavaの信頼ストア、AgentはOSの信頼ストアに設定する。検証無効化を設定しない。
Windows＋WSLでlocalhost転送がIPv6のみの場合、Windows側の接続先を`http://[::1]:8080`、配布先を`tcp://[::1]:18884`へ変更する。バックエンドがコンテナ内にある場合、管理接続先はコンテナから到達できる値を別に指定する。

### 4. Agentを常駐実行

登録方法はAgent設計リポジトリの`docs/07_OperationsDesign/010_registration_test_guide.md`を参照。既存の登録情報を使って、同じ保存先で実行する。

```powershell
$agentTestDir = Join-Path $env:LOCALAPPDATA "MomiServer\agent-test"
.\momiserver-agent.exe run --state-dir $agentTestDir --allow-insecure-localhost --allow-insecure-mqtt-localhost
```

通常のHTTPS/TLS環境では2つの開発用オプションを省略する。プロセス終了はCtrl+C。LinuxのSIGTERMにも対応する。
runは保存先のロックを保持するため、同じ保存先へのregister/status/refreshや2重runは拒否する。認証情報更新はrun自身が実施する。
OSの自動起動にはsystemd等からこのrunコマンドを起動し、登録時と同じアカウント・保存先を指定する。サービスの自動登録・インストーラーは今回の範囲外。

### 5. バッチ作成・実行・確認

管理画面のバッチ定義に、端末上に存在するコマンド、必要な作業ディレクトリ、タイムアウトを設定する。初回は`echo runtime-test`のような処理で確認する。
管理ユーザー認証で`POST /api/batch/executions/definitions/{batchDefinitionId}`へ
`target_agent_id`（端末マスタ数値ID）と必要に応じ`test_execution`を指定する。
MQTT Client IDやAgent UUIDをtarget_agent_idに使わない。
`GET /api/batch/executions/{executionId}`で状態・終了コード・stdout/stderrを確認する。キャンセルは管理者の`POST /api/batch/executions/{executionId}/cancel`。
Agentの状態は端末マスタでMQTT設定同期状態、最終ハートビート、MQTT最終報告を確認する。

## 設定管理

- 共通設定APIの同時実行上限を60秒ごとに取得する。初回取得前は実行しない。実行中のタイムアウト・猶予・再送条件は要求作成時のスナップショットを使用する。
- 実行環境の初期値は`GET /api/v1/agent/runtime/environment`から取得。Windowsはcmd.exe /D /S /C、Linux/macOSは/bin/sh -c。バックエンドの`AGENT_WORKING_DIRECTORY`（app.agent-runtime.working-directory）で既定作業ディレクトリを指定可能。
- 保存先のconfig.jsonでshell、shell_args、working_directoryを指定可能。シェル指定時は引数も設定する。作業ディレクトリは実行要求指定、端末設定、バックエンド既定の順。全て未指定ならAgentの起動ディレクトリ。
- パラメータは要求作成時に固定値・current_date/current_timeを解決し、JSONへ保存する。予定実行は予定日時、手動実行は要求日時を基準とする。未対応のパラメータ種別は要求作成を拒否する。
- 子プロセスへMOMISERVER_PARAMETERS_JSONとMOMISERVER_EXECUTION_IDを渡す。パラメータをシェルコマンドへ文字列展開しない。スクリプト側でJSONを解析する。

## 処理・復旧の契約

- MQTTトピックは`smarthome/agents/ma-{Agent UUID}-{generation}/batch/command`と`.../cancel`。命令通知から直接実行せず、HTTPで対象端末・世代に割り当てられた内容を取得する。
- clean sessionとQoS 1を使用。MQTT再接続に加え10秒ごとにHTTP照合するため、オフライン中の要求はDBから回復する。MQTTセッションを実行履歴の保存先にしない。
- 実行前にexecutions/{executionId}.jsonへstartingを原子的保存し、開始APIの受理後にプロセスを起動する。開始APIの応答不明や、再起動時にstarting/runningが残った場合は未確認扱いとし自動再実行しない。
- 同一IDの再配信は起動しない。実行済みIDの記録は保持期限で消さず、再実行はバックエンドから新しい実行IDを発行する。
- Windowsは起動停止状態でJob Objectへ割り当ててから再開し、終了時に子プロセスも停止する。UNIXはプロセスグループにTERM、猶予後KILLを送る。自らグループを離脱するデーモン化処理は対応対象外。
- 成否不明はunknownという終端状態をサーバーへ送らず、既存のstart_unconfirmed/result_unconfirmed/stop_unconfirmedへ反映する。Agent停止だけでOS上の処理の成否を推測しない。
- 出力はstdout/stderr各64KiBを上限に収集し、超過分は読み捨ててoutput_truncatedを付ける。結果をJSON保存後にHTTP返却する。
- 確定結果は同一実行ID・終端状態の受理応答を確認するまで、日数・試行回数に上限を設けず保持・再送する。試行回数・次回予定は送信前に保存する。
- 旧要求の`result_retry_count`・`unsent_result_retention_days`は受信互換用であり、新Agentの再送停止・結果削除には使用しない。再送間隔・HTTP期限・プロセス期限は維持する。
- `--retry-results`は保存済みの再送待機だけを解除する。試行回数をリセットせず、プロセスを再実行しない。
- 設定・MQTT取得より先に保存済み結果の送信を試す。失効・通信エラー・不正な受理応答では本文を破棄しない。受理状態の保存失敗時もメモリと既存ファイルの結果を保持する。
- 未受理結果がある間は新規プロセスの起動を抑制する。実行中の監視・取消は続ける。別登録世代の結果は自動で別世代へ送らず、照合・復旧まで保持する。
- ハートビートは60秒。失敗時10秒間隔で3回再送し、上限後は次周期へ移る。サーバー受信後3分以内をonlineとする。詳細なCPU等の監視は未実装。
- Dynamic Security同期は5秒間隔。資格情報はDBへ暗号化保存してからブローカーへ反映する。無効化・再登録・端末無効化では旧接続を拒否・切断し、旧世代の失効確認後に新世代を有効化する。

2026/10/31に上限撤廃・受理照合・新規開始の抑制を実装した。
保存領域の破損・喪失、書込み不能時のまだ永続化できていない結果、長時間停止の実機受入は別の限界・確認項目とする。
空き容量と認証を復旧するまで、未受理結果や実行IDを手作業で削除して再開しない。

## 計画更新の停止・再開

1. 新Agentを先に導入し、既存の開始・結果送信が正常であることを確認する。
2. BEの`BATCH_MAINTENANCE_MARKER`に専用アカウントが書込可能な永続パスを指定する。清掃・デプロイで削除しない。
3. ADMINの[保守API](../05_API_Design/API/0050_batch_maintenance.md)へ`paused=true`を保存する。
4. 結果受信・取消・Agentを動かしたまま、`safe_to_stop=true`を待つ。実行中・配信済み・開始未確認が残れば停止可としない。
5. 開始未確認の残件は端末と照合する。自動で成功・失敗・停止済みへ変換したり、更新都合で強制終了したりしない。
6. 停止可能になってからBE停止、バックアップ、更新、DB移行を行う。バッチ以外の書込停止範囲は本番受入で確認する。
7. BE再起動後も保守状態が残ることを確認し、接続・移行・結果受信が正常ならADMINが`paused=false`を保存する。

このAPIは単一BE用であり、OSサービスを直接停止しない。保守中も手動要求の作成と予定要求の計画は継続し、開始とMQTTの通常配信だけを止める。
解除後も元の開始期限を適用し、期限を過ぎた処理を更新都合で遡って実行しない。
保守状態のファイルを外部から削除・編集しても稼働中BEへは反映しない。変更はAPIで排他・永続化する。
保存失敗はエラーとし、再起動時にマーカーが破損・読取り不能なら起動を失敗させる。
電源断や保存媒体喪失まで含む耐障害性の受入は未実施であり、単純な再起動試験と区別する。

## 障害対応

| 事象 | 対応 |
| --- | --- |
| MQTT未構成 | AGENT_MQTT_ENABLEDと接続先のTLS/開発許可条件を確認 |
| 同期待ち | 管理ユーザー・プラグイン・ネットワークを確認。DBの資格情報行は再試行の基準なので削除しない |
| MQTT接続失敗 | Agentから到達可能なURLか、TLS信頼・Client ID・専用資格情報を確認。共有パスワードへの置換はしない |
| 実行記録破損 | 起動を停止する。記録を削除して再実行せず、サーバーと実プロセスを照合する |
| 結果未確認 | プロセスの有無と保存済み結果を確認。再実行と結果再送を区別する |
| ブローカー設定を喪失 | Dynamic Securityの永続ファイルをバックアップから復旧する。DBだけreadyでもブローカーを自動再初期化しない |

## 備考

既存の開発DB・稼働中Mosquittoへの自動適用は実施しない。検証には専用DB・ブローカー・バックエンドを使用する。既存サービスとポートを共用せず、今回の添付検証ではDB 55433、ブローカー18885、バックエンド18082を使用した。
Dynamic Securityの形式は[公式ドキュメント](https://mosquitto.org/documentation/dynamic-security/)と[2.0系プロトコル](https://github.com/eclipse-mosquitto/mosquitto/blob/v2.0.22/plugins/dynamic-security/README.md)を参照した。

## バッチ定義への添付の導入・操作

1. DBへV25を適用する。既存V22～V24は前提。
2. バックエンド実行環境に専用の保存ディレクトリを作成し、サービスアカウントだけに必要な読み書き権限を付ける。BATCH_FILE_STORAGE_ROOTに絶対パスを指定して再起動する。コンテナでは永続ボリュームをマウントし、そのコンテナ内パスを指定する。
3. バッチ共通設定画面で添付上限と作業領域保持を設定する。初期値は合意仕様に従う。設定ファイルで変更するキーはapp.batch.defaults.attachment-max-file-mib、attachment-max-total-mib、attachment-max-count、work-directory-retention-days（後3項目も同じprefix）。DB保存値を優先する。
4. バッチ定義を保存し、「実行用ファイル」でスクリプト等を選択する。種別と相対配置先を指定し「添付を確定」する。複数選択・ドロップにも対応。各ファイルの確定は即時で、定義の保存やキャンセルとは別操作。
5. 実行コマンドにはcmd.exe /D /C run.cmd、powershell.exe -File scripts/run.ps1、/bin/sh scripts/run.shなど、端末にあるインタープリターと添付の相対パスを指定する。
6. 登録済みAgentのrunを起動して実行要求を作る。新要求は保存先のwork/{executionId}を使用する。旧要求は従来の作業ディレクトリ契約のまま。結果・重複防止記録はexecutions配下に保持する。

HTTP multipartの輸送上限はBATCH_MULTIPART_MAX_FILE_SIZE（既定10240MB）とBATCH_MULTIPART_MAX_REQUEST_SIZE（既定10241MB）。共通設定の上限はその内側で別途適用する。前段プロキシにも対応する上限・タイムアウトが必要。添付はバックエンド一時ディスクへ退避されるため空き容量を確保する。

作業領域の回収は終了確認後の保持期限到達時に行い、Agent起動中に再試行する。結果送信が未完了でも結果JSONは削除しない。停止確認不能な領域は自動削除しない。
バックエンドの旧添付実体・クラッシュ時一時ファイルの自動回収は未実装であり、バックアップと容量監視を行う。
