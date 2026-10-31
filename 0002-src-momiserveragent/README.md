# MomiServer Agent

Go製Agentの登録・認証CLI。Windows / Linux / macOSに対応する。
HTTP登録・資格情報更新・認証確認に加え、runによるMQTT命令受信・バッチ実行・結果返却・端末およびOSサービスの状態送信を実装している。定義添付の取得・検証・実行と作業ディレクトリの期限後回収も実装済み。OSサービス自動登録と成果物アップロードは未実装。

## 初回登録

端末マスタで基本情報を保存し、管理画面からAgent登録コードを発行する。

```text
momiserver-agent register <登録コード> --server https://momiserver.example.com
```

コードを省略すると非表示で対話入力する。引数入力はシェル履歴・プロセス一覧へ残り得るため、対話入力を推奨する。
HTTPS証明書をOSが信頼できるよう構成する。TLS検証の無効化やリダイレクト追従は行わない。

## localhostでの開発テスト

`--allow-insecure-localhost`を明示した場合だけ、`http://localhost`、`http://127.0.0.1`、`http://[::1]`を許可する。任意のポートを指定できる。LANアドレスや他のホスト名へのHTTPは許可しない。
localhostはDNSを利用せず127.0.0.1へ接続する。IPv6のみで待ち受ける場合は`http://[::1]:8080`を指定する。開発用HTTPではプロキシを使用せず、リダイレクトにも追従しない。HTTPSの証明書検証は通常どおり行う。

Windows PowerShellで、`dist`ディレクトリから実行する例:

```powershell
$agentTestDir = Join-Path $env:LOCALAPPDATA "MomiServer\agent-test"
.\momiserver-agent.exe register --server "http://localhost:8080" --state-dir $agentTestDir --allow-insecure-localhost
.\momiserver-agent.exe status --state-dir $agentTestDir --allow-insecure-localhost
.\momiserver-agent.exe refresh --state-dir $agentTestDir --allow-insecure-localhost
```

register実行後、登録コードを対話入力する。保存先は自動作成する。
HTTP許可は保存せず、保存済みURLやconfig.jsonがHTTPでも毎回オプションを必要とする。これはローカル開発用であり、通常の接続はHTTPSを使用する。
バックエンドには`AGENT_RESPONSE_ENCRYPTION_KEY`（32バイトのランダム値を標準Base64化したもの）の設定と再起動が必要。

## 保存先と接続先

| OS | デフォルト保存先 |
| --- | --- |
| Windows | `%ProgramData%\MomiServer\agent` |
| Linux | `/var/lib/momiserver-agent` |
| macOS | `/Library/Application Support/MomiServer/agent` |

`--state-dir <絶対パス>`で変更できる。システム共通パスへの初回書き込みには適切な権限が必要。
サービス化する際は同じ実行アカウント・同じ保存先を指定する。WindowsではSYSTEMも読み書き可能とする。
UNIXはディレクトリ0700・資格情報0600、Windowsは実行アカウントとSYSTEMだけのDACLで保護する。

同じディレクトリの`config.json`に`server`を設定できる。優先順位は`--server`、config.json、保存済み接続先。
登録済み接続先を変更して資格情報を別サーバーへ送ることは拒否する。

`credentials.json`には接続先、Ed25519秘密鍵、登録要求ID、登録コードのハッシュ、取得済み資格情報、更新中の要求IDを保持する。
バッチ結果ファイルとは分離する。平文登録コードは保存しない。
一時ファイルの同期後に原子的に置換し、プロセス間ロックで同時操作を拒否する。クラッシュ後にロックは解放される。
秘密鍵が含まれるため保存ディレクトリを別端末へ複製しない。

## 認証確認と更新

```text
momiserver-agent status
momiserver-agent refresh
```

statusは必要に応じて更新してから自分の登録状態を取得する。期限5分前または未確定の更新要求がある場合に更新する。
refreshは明示的に更新する。更新要求IDを先に保存し、応答喪失後も同一要求として再試行する。
run実行中は認証情報を自動更新する。HTTP認証成功とMQTT接続成功は別。statusにはバックエンドから取得したMQTT設定同期状態を表示する。

## バッチ常駐実行

バックエンドのV23・V24マイグレーションとMosquitto Dynamic Securityの準備後、登録時と同じ保存先を指定する。

```powershell
.\momiserver-agent.exe run --state-dir $agentTestDir --allow-insecure-localhost --allow-insecure-mqtt-localhost
```

通常のHTTPS/TLS環境では開発用オプションを省略する。run中は同じ保存先への別CLI操作をロックする。終了はCtrl+C、LinuxではSIGTERMも使用可能。
実行ID・開始意図・結果・再送回数をexecutions配下のJSONへ原子的に保存する。再起動時に成否が不明な処理は再実行しない。
確定結果はサーバーが同じ実行ID・結果状態の受理を返すまで、保持日数・再送回数に上限を設けず再送する。
旧要求の`result_retry_count`・`unsent_result_retention_days`は受信互換のため残すが、再送停止や結果削除には使用しない。
再送間隔・通信タイムアウト・プロセスの実行期限・標準出力の保存上限は維持する。
`--retry-results`は保存済みの再送待機だけを解除し、試行回数や実行防止記録はリセットしない。

未受理結果が1件でもある場合は容量保護のため新規起動を抑制する。実行中の監視・取消・結果送信は継続する。
登録世代が変わった結果は別端末として送らず保持し、運用者が照合する。認証失効・容量不足を理由に未受理結果を自動削除しない。
結果の保存先が満杯・書込み不能の場合は新しい処理を開始せず、空き容量・権限・認証を復旧する。保存先全体の破損や喪失まで保護する方式ではない。

Backend保守中に開始APIが同一IDの`queued`または`dispatched`を返した場合は、未開始の応答として待機し、次周期に再照合する。
通信切断・不明な応答はこのケースと区別し、従来どおり自動再実行しない。
保守制御の導入はAgentを先に更新し、Backendを後に更新する。更新時はAgentを先に停止せず、Backend側で新規開始を止めて実行中処理の完了を待つ。
パラメータは環境変数MOMISERVER_PARAMETERS_JSON、実行IDはMOMISERVER_EXECUTION_IDで渡す。stdout/stderrは各64KiBまで保存し、超過時は切り捨てを報告する。
実行環境の初期値をバックエンドから取得し、保存先config.jsonのshell・shell_args・working_directoryで上書きできる。実行ユーザーはAgentのOSアカウント。
ブローカー構築・バックエンド環境変数・バッチ要求API・制約の正本は、バックエンド設計リポジトリの`docs/07_OperationsDesign/0004_agent_batch_runtime.md`を参照。

## 端末・サービス状態送信

runはバックエンドから監視対象サービスを取得し、端末のCPU、メモリ、稼働時間、ストレージ、取得可能な温度と、対象OSサービスの状態を定期送信する。
温度はOS、ハードウェア、ドライバーにより取得できない場合があり、その場合は未取得として送信する。

Windowsのサービス状態参照には、Service Control Managerへの接続権限と対象サービスの状態照会権限だけを使用する。
管理者相当の全権限は要求しない。Linuxではsystemdのサービス名を使用する。macOSのサービス状態取得は未対応。

## 応答喪失と再登録

登録途中で通信断・再起動が発生した場合、同じ接続先・保存先・登録コードでregisterを再実行する。
鍵と要求IDを保持して新しいnonceへ署名する。保存済み資格情報がある場合、registerは上書きせずstatusの使用を案内する。

管理者が端末マスタで再登録を開始した後、新しいコードで次を実行する。

```text
momiserver-agent register <新コード> --reregister
```

異なるコードへ切り替える場合も明示オプションが必要。登録途中の同じコードによる再試行は、--reregisterを再指定しても鍵・要求IDを作り直さない。
壊れたJSONは自動上書きしない。管理者が保存領域と再登録の要否を確認する。
成功は終了コード0、入力・通信・認証・保存の失敗は1。標準出力・エラーにコード・トークン・秘密鍵を出力しない。

## ビルド・確認

Go 1.25.14以上を使用する。CIは公式`golang:1.25.14-bookworm`で標準ライブラリを含めてビルドする。x/termとx/sysを非表示入力・OS権限・ファイルロック・プロセス制御に使用し、MQTTにはEclipse Paho Goクライアントを使用する。

2026-10-23の脆弱性報告と照合し、Go 1.25.0で該当するURL解析・証明書検証など9件の修正を含む同一minorのパッチ版へ最低要件を更新した。対象はCVE-2025-47912、CVE-2025-58185、CVE-2025-58187、CVE-2025-58188、CVE-2025-58189、CVE-2025-61723、CVE-2025-61724、CVE-2025-61727、CVE-2025-61729。報告書中のGo外部モジュールには現行依存との一致がなかったため、モジュールの一括更新は行わない。

`go.mod`の変更だけでは配布済み実行ファイルの標準ライブラリは更新されない。修正版コンパイラーで再ビルドし、各端末へ配置し直すこと。`go version -m <実行ファイル>`でビルドに使われたGoバージョンを確認する。この対応は添付報告の範囲であり、Go 1.25系のサポート状況と後継minorへの更新は[公式リリース履歴](https://go.dev/doc/devel/release)に従って別途管理する。

```text
go test ./...
go vet ./...
go build -trimpath -o dist/momiserver-agent .
```

Windows上でCGO_ENABLED=0、GOOS=windows/linux/darwin、GOARCH=amd64/arm64を設定して各OS用バイナリを生成できる。
macOSの署名・公証、OS別インストーラー、サービス登録は別工程。実行確認は各OS上でも必要。
`TestLiveBackend`は分離した検証サーバーのAGENT_TEST_SERVERと単回使用のAGENT_TEST_CODEがある場合のみ実行する。通常はスキップする。

## 定数定義

共通方針 `08_定数定義方針.md` に従い、登録・認証・常駐実行が共用する固定値は
`internal/registration/constants.go` にまとめる。パッケージ内だけで使うため、名前は非公開のlowerCamelCaseとする。

Backendの実行状態、ローカルジャーナルの段階、実測サービス状態は契約が異なるため、別の定数群として扱う。
値が同じという理由だけでは統合しない。既存のJSONコード、Agent版、通信期限は維持し、互換性テストで確認する。
OS固有の状態値はOSのライブラリ定義を優先し、テストデータや外部依存モジュールを移動・定数化しない。

## Goコーディング規約

共通規約の参照先とプロジェクト固有の注意事項は [AGENTS.md](AGENTS.md) に記載する。
公開APIは識別子名から始まるdoc commentで、事前条件・失敗時の扱い・保存や解放の責任を説明する。
`go doc ./internal/registration` で確認できる。公開宣言への説明の付与は既存の `go test ./...` でも検査する。

## 関連設計

バックエンド設計リポジトリの`docs/02_SystemDesign/認証/0005_agent_authentication.md`と`docs/05_API_Design/API/0041_agent_registration_authentication.md`を契約の正本とする。
