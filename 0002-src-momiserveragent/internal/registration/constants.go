package registration

import "time"

// maxResultAttempts は再送回数の観測値を飽和させる値であり、再送自体の上限ではない。
const maxResultAttempts = int(^uint(0) >> 1)

// version は登録・ハートビートで通知するAgentの版。保存済み状態との互換性を維持する。
const version = "0.1.0"

// 認証通信の期限と更新猶予。同じregistrationパッケージ内でのみ使用する。
const (
	httpRequestTimeout = 30 * time.Second
	accessRefreshLead  = 5 * time.Minute
)

// Backendへ送信する実行状態。文字列型と既存のJSONコードを維持する。
const (
	executionRunning   = "running"
	executionSuccess   = "success"
	executionFailed    = "failed"
	executionTimeout   = "timeout"
	executionCancelled = "cancelled"
	executionExpired   = "expired"
	executionUnknown   = "unknown"
)

// ローカルジャーナルの処理段階。Backendの実行状態とは異なる契約として管理する。
const (
	phaseStarting     = "starting"
	phaseRunning      = "running"
	phaseCompleted    = "completed"
	phaseAcknowledged = "acknowledged"
	phaseUnknown      = "unknown"
)

// 実測したサービスの状態。OS固有の状態名ではなくBackend共通のコードを表す。
const (
	runtimeOnline  = "online"
	runtimeOffline = "offline"
	runtimeUnknown = "unknown"
)
