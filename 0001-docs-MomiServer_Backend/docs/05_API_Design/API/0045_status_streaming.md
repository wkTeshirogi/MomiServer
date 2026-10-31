# マシン・サービス状態取得／リアルタイム配信

## 基本情報

| 項目 | 内容 |
| --- | --- |
| API ID | API-0045 |
| 分類 | 状態監視 |
| メソッド | GET、POST、WebSocket |
| パス | `/api/status/machines`、`/api/status/services`、`/api/status/websocket-ticket`、`/ws/machines`、`/ws/services` |
| 認証 | 必須 |
| 権限 | マシン: ADMIN・OPERATOR、サービス: ADMIN・OPERATOR・USER |
| 正常ステータス | HTTP 200、WebSocket 101 |

## 概要

マシンマスタ、サービスマスタ、登録済みAgentのHeartbeatと最新の実測状態から画面用スナップショットを生成する。
画面はHTTP APIで初期状態を取得し、利用者がリアルタイム更新を有効にした場合はWebSocketで全件スナップショットを受信する。

## 処理概要

1. 画面表示時に、対象画面のHTTP APIから初期スナップショットを取得する。
2. リアルタイム更新開始時は、JWT認証済みのHTTP POSTで一回限りの接続チケットを発行する。
3. 発行後30秒以内に、返却されたWebSocketパスへチケットをクエリパラメータとして渡す。
4. バックエンドはチケットの対象チャネル、有効期限、未使用状態を検証し、接続時と5秒間隔で全件スナップショットを送信する。
5. チケットはハンドシェイク時に消費し、再接続時は新しいチケットを発行する。

ブラウザ標準WebSocket APIはハンドシェイクへ任意のAuthorizationヘッダーを設定できない。
このためJWTをURLへ露出させず、短寿命かつ一回限りのチケットへ交換する方式を採用する。

## HTTP API

| メソッド | パス | 権限 | 説明 |
| --- | --- | --- | --- |
| GET | `/api/status/machines` | ADMIN・OPERATOR | マシン状態の初期スナップショット |
| GET | `/api/status/services` | ADMIN・OPERATOR・USER | サービス状態の初期スナップショット |
| POST | `/api/status/websocket-ticket` | チャネルに準拠 | WebSocket接続チケット発行 |

## チケット発行リクエスト

| 項目 | 型 | 必須 | 制約 | 説明 |
| --- | --- | --- | --- | --- |
| `channel` | string | 必須 | `machines` または `services` | 接続対象チャネル |

## チケット発行レスポンス

| 項目 | 型 | 説明 |
| --- | --- | --- |
| `ticket` | string | UUID形式の一回限りチケット |
| `websocket_path` | string | `/ws/machines` または `/ws/services` |
| `expires_at` | datetime | チケット有効期限 |

## WebSocketメッセージ

| チャネル | `type` | 配列項目 | 説明 |
| --- | --- | --- | --- |
| `/ws/machines` | `machine_update` | `machines` | マシン全件スナップショット |
| `/ws/services` | `service_update` | `services` | サービス全件スナップショット |

両メッセージに生成日時 `generated_at` を含める。クライアントからのメッセージ送信は許可せず、受信した場合はPolicy Violationとして接続を閉じる。

## 状態判定

| 対象 | 条件 | 状態 |
| --- | --- | --- |
| マシン | マスタ状態が`90` | `maintenance` |
| マシン | マスタ状態が`99` | `offline` |
| マシン | 登録済みAgentのHeartbeatがない | `unknown` |
| マシン | Heartbeatが現在から3分以内 | `online` |
| マシン | Heartbeatが現在から3分を超過 | `offline` |
| サービス | マスタ状態が`stop`または`maintenance` | `maintenance` |
| サービス | 対象マシンが`online`以外 | 対象マシンの状態 |
| サービス | 対象マシンが`online`で3分以内のAgent実測状態がある | Agent実測状態 |
| サービス | マスタ状態が`online`または`offline` | マスタ状態 |
| サービス | 上記以外で対象マシンが`online` | `unknown` |

端末状態とサービス状態は、現在のAgent登録世代と一致し、収集日時が現在から3分以内の場合だけ実測値として採用する。
端末はCPU、メモリ、取得可能な温度、ストレージ、稼働時間を返す。
サービスはOSサービス状態、PID、プロセスCPU・メモリ、稼働時間を返す。
古い世代、3分を超えた状態、OSから取得できない項目は推測値で補完せず、
`metricsAvailable=false`または該当値の未取得表示とする。ゲーム固有のプレイヤー数等は今回の収集対象外とする。

端末の`healthScore`はCPU使用率とメモリ使用率の大きい方を100から減じた参考値とする。
ストレージ容量はAPIでMiBへ変換する。`observedAt`は実測値採用時は収集日時、それ以外はHeartbeat日時を表す。

## 主なエラー

| HTTPステータス | code | 説明 |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | チャネル未指定または不正 |
| 401 | `UNAUTHORIZED` | HTTP APIが未認証、またはWebSocketチケットが無効・期限切れ・使用済み |
| 403 | `ACCESS_DENIED` | 対象チャネルを利用できないロール |

## 備考

- 配信間隔は `STATUS_WEBSOCKET_BROADCAST_INTERVAL_MS`、チケット有効秒数は `STATUS_WEBSOCKET_TICKET_TTL_SECONDS` で変更できる。
- WebSocketのServlet認可はハンドシェイクInterceptorへ委譲する。チケット発行時と消費時の両方で対象チャネルを検証する。
- Agentのメトリクス収集を追加する場合も本API契約を拡張し、未取得値と実測値を区別する。
