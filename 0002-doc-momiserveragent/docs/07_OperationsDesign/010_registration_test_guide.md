# Agent登録・認証テスト操作手順

## 概要

2026年9月23日時点のGo製AgentをWindows PowerShellでビルドし、ローカルバックエンドへ登録・認証確認・更新する手順を記載する。記載したコマンドは操作用であり、実装ソースの転載ではない。

## 対象

| 項目 | 内容 |
| --- | --- |
| 対象システム | MomiServer Agent、バックエンド、管理画面 |
| 対象環境 | Windows、Go 1.25以上、開発用バックエンド `http://localhost:8080` |
| 対象作業 | ビルド、自動テスト、登録、認証確認、更新、再登録 |

HTTP登録・認証確認・資格情報更新に加え、runによるMQTT接続・バッチ実行・結果返却・軽量ハートビートを実装した。定義添付の取得・検証・実行と作業ディレクトリの期限後回収も実装済み。OSサービス自動登録、詳細端末監視、成果物アップロードは未実装。
登録完了、HTTP認証成功、MQTT接続成功、監視上のOnlineは別々に判定する。

## 運用手順

| No | 作業 | 内容 | 実施者 |
| --- | --- | --- | --- |
| 1 | DB・バックエンド起動 | DB接続、専用鍵、必要なマイグレーションを確認する | 開発者 |
| 2 | Agent準備 | 自動テストとWindows用ビルドを行う | 開発者 |
| 3 | 登録コード発行 | 管理画面の端末マスタでテスト用端末を保存して発行する | 管理者 |
| 4 | 登録 | 下記registerを実行し、発行したコードを対話入力する | 端末操作担当 |
| 5 | 認証確認・更新 | status、refresh、statusの順に確認する | 端末操作担当 |

### 1. バックエンドの専用鍵

環境変数`AGENT_RESPONSE_ENCRYPTION_KEY`に、32バイトの暗号学的乱数を標準Base64化した値が必要。登録・更新の短期復旧応答の暗号化用であり、Agentへ配布する鍵ではない。
未設定の場合のみ、PowerShellで一度生成する。

```powershell
$keyBytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($keyBytes)
$rng.Dispose()
[Convert]::ToBase64String($keyBytes)
```

IntelliJではバックエンドを起動する実行構成の「環境変数」に`AGENT_RESPONSE_ENCRYPTION_KEY=生成値`を設定して再起動する。シェルから起動する場合は、そのプロセスが同じ環境変数を継承するよう設定する。
起動ごとに作り直さず、同じ鍵を保持する。実値はGit、手順書、チャットへ記録しない。鍵変更の影響やバックアップ方針はバックエンド設計リポジトリの`docs/07_OperationsDesign/0002_configuration_management.md`を参照する。

### 2. ビルドと自動テスト

```powershell
cd D:\6000.PROJECTS\MomiServer\0002-src-momiserveragent
go test -v ./...
go vet ./...
go build -trimpath -o .\dist\momiserver-agent.exe .
cd .\dist
```

前のコマンドが成功したことを確認して次へ進む。通常のテストは模擬サーバーで実行できる。`TestLiveBackend`は専用検証環境の`AGENT_TEST_SERVER`と`AGENT_TEST_CODE`がない場合にスキップされるため、通常のSKIPは異常ではない。
実バックエンド用テストは登録コードを消費し、ローカルCLIの資格情報を保存する操作ではない。日常の手動確認には下記CLIを使用する。

### 3. localhostへの登録

```powershell
$agentTestDir = Join-Path $env:LOCALAPPDATA "MomiServer\agent-test"
.\momiserver-agent.exe register --server "http://localhost:8080" --state-dir $agentTestDir --allow-insecure-localhost
```

実行後の「登録コード:」へ入力する。文字は表示されない。保存先は自動作成され、事前作成は不要。書き込み可能な場所を指定する。
コードを引数で渡す形式も使えるが、履歴やプロセス一覧へ残るため対話入力を推奨する。成功時は「Agent登録情報を保存しました。」とAgent IDが表示される。

`--server`はURLだけを指定する。`http:localhost:8080`や`[http://localhost:8080](http://localhost:8080)`は使用しない。保存先のオプション名は`--state-dir`であり、`--stage-dir`ではない。

### 4. 認証確認・更新

```powershell
.\momiserver-agent.exe status --state-dir $agentTestDir --allow-insecure-localhost
.\momiserver-agent.exe refresh --state-dir $agentTestDir --allow-insecure-localhost
.\momiserver-agent.exe status --state-dir $agentTestDir --allow-insecure-localhost
```

「登録状態: registered」「認証確認: 成功」が確認基準。終了コードは成功0、失敗1。PowerShellでは直後の`$LASTEXITCODE`で確認できる。
保存済みURLを利用するため、同じ保存先なら`--server`は省略可能。PowerShellを開き直した場合は`$agentTestDir`の代入も再実行する。
statusは必要に応じて資格情報を更新してから自分の状態を取得する。refreshは明示的に更新する。常駐・定期更新を開始するコマンドではない。

### 5. 応答消失・再登録

登録中に通信断となった場合は、同じ保存先・URL・コードでregisterを再実行する。資格情報保存後はregisterを繰り返さずstatusを使用する。
端末交換や鍵喪失などで再登録が必要な場合は、管理画面で再登録を開始し、新しいコードを発行してから次を実行する。

```powershell
.\momiserver-agent.exe register --server "http://localhost:8080" --state-dir $agentTestDir --allow-insecure-localhost --reregister
```

実行後に新しいコードを入力する。単なるMQTT未構成を理由に再登録しない。

## 開始・停止手順

| 操作 | 手順 | 確認方法 |
| --- | --- | --- |
| 開始 | 登録後にrunを実行 | 常駐・MQTT接続・バッチ照合を開始 |
| 停止 | runはCtrl+C、register/status/refreshは処理完了で終了 | OSサービスの自動登録は行わない |

## 設定管理

| 設定 | 管理場所 | 変更時の注意 |
| --- | --- | --- |
| 保存先 | `--state-dir` | 全操作で同じパスを使用する。省略時のOS別保存先は[登録設計](../02_SystemDesign/Details/010_agent_registration.md)を参照 |
| 接続先 | `--server`、config.jsonのserver、保存済みURLの優先順 | 保存済みと異なる接続先への送信は明示的な再登録が必要 |
| 資格情報 | 保存先内のcredentials.json | 秘密鍵を含む。Git管理や他端末への複製をしない |
| 開発用HTTP | `--allow-insecure-localhost` | 許可は保存しない。register / status / refreshごとに指定する |

開発用HTTPは`localhost`、`127.0.0.1`、`[::1]`に限定する。localhostはDNSを使わず127.0.0.1へ接続するため、IPv6のみの待受では`http://[::1]:8080`を指定する。
LANアドレスや外部ホストはこのオプションでも許可しない。プロキシを使わず、リダイレクトも追従しない。通常運用のHTTPS証明書検証を無効化するオプションではない。
通常運用ではOSが信頼する証明書のHTTPS URLを指定し、開発用オプションを付けない。

## 障害対応

| 事象 | 確認内容 | 対応 |
| --- | --- | --- |
| generateJooqでlocalhost:5432接続拒否 | DBの稼働状態・DB_HOST/DB_PORTを確認 | この開発環境では`podman start zunda-db`後、`podman exec zunda-db pg_isready`で確認し、バックエンドを再実行する |
| Agent認証の専用鍵が未設定 | バックエンド実行プロセスの環境変数を確認 | 上記専用鍵の設定を実施。未設定のほかBase64不正・32バイトでない場合も同じ表示となる |
| コマンド引数を確認してください | サブコマンド・オプションの綴り | registerを指定し、`--state-dir`を使用する |
| ベースURL／HTTPSエラー | URL表記・開発オプション | Markdownリンクを貼らずURLだけ指定し、localhost HTTPでは毎回オプションを付ける |
| 認証できません | コードの期限・端末の登録状態 | 未登録ならコード再発行、登録済みならstatus。再登録が必要なら管理画面から開始する |
| バックエンドへ接続できません | バックエンド起動・ポート・HTTPS証明書 | 接続先を確認し、保存済み要求を保持して再試行する |
| MQTT設定状態: not_configured | バックエンド設定 | 下記の専用MQTT導入設定を確認する |

## MQTTと常駐実行

Dynamic SecurityによるMosquitto専用認証・端末別ACL・失効連携を実装した。既定は無効で、プラグインとバックエンド環境変数の準備が必要。
導入手順・設定の正本はバックエンド設計リポジトリの`docs/07_OperationsDesign/0004_agent_batch_runtime.md`、APIは`docs/05_API_Design/API/0042_agent_runtime.md`を参照する。
準備後、同じ保存先で次を実行する。

```powershell
.\momiserver-agent.exe run --state-dir $agentTestDir --allow-insecure-localhost --allow-insecure-mqtt-localhost
```

run中は認証を自動更新し、MQTT通知と10秒周期のHTTP照合により自分宛ての実行要求を取得する。終了はCtrl+C。同じ保存先へのstatus/refreshの同時操作は拒否する。
ブローカー・バックエンド用の共有秘密をAgentへ手動で設定しない。実行情報と未送信結果は保存先のexecutionsへ保存する。結果再送上限後、保持中の結果だけを再送する操作は`run --retry-results`。バッチの再実行ではない。
登録応答は「MQTT接続設定はrunで取得します」と案内し、statusでは現在の設定同期状態を表示する。readyはMQTT接続中という意味ではない。

## 備考

- 2026年9月23日、利用者からCLIの「Agent登録情報を保存しました。」とAgent IDの表示を確認した旨の報告を受けた。HTTP登録・ローカル保存の成功報告として記録する。status / refreshの利用者環境での結果、MQTT接続成功は未確認。
- 開発用HTTP追加時の検証は自動テスト8件成功、go vet成功、Windows用exeのビルド成功。実バックエンド用テストは未実施（通常実行ではSKIP）。
- 登録コード・秘密鍵・アクセストークン・更新トークンの実値は本書へ記録しない。
