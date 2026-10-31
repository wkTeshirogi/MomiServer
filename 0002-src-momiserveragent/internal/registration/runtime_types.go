package registration

import (
	"encoding/json"
	"errors"
	"os"
	"path/filepath"
	"time"
)

// execution はBackendから取得した実行要求と、その時点で確定した実行・保存条件を保持する。
type execution struct {
	// Attachments は実行前にサイズ・ハッシュを検証して配置する添付一覧を表す。
	Attachments []attachment `json:"attachments"`
	// WorkRetentionDays は作業領域の保持日数を表す。0は旧要求のディレクトリ契約を維持する。
	WorkRetentionDays int `json:"work_directory_retention_days"`
	// Parameters はシェルへ展開せずJSON環境変数として渡す入力値を保持する。
	Parameters map[string]string `json:"parameters"`
	// ID はジャーナル名・作業領域名にも使用する検証対象の実行UUIDを表す。
	ID string `json:"execution_id"`
	// Status はBackendの実行状態コードを表す。
	Status string `json:"execution_status"`
	// Command は設定されたシェルへ渡す実行コマンドを表す。
	Command string `json:"execution_command"`
	// Directory は旧要求の作業ディレクトリを表す。新要求では専用作業領域へ置き換える。
	Directory string `json:"working_directory"`
	// Timeout は添付準備を含む実行制限時間を秒単位で表す。
	Timeout int `json:"timeout_seconds"`
	// Deadline はBackend指定タイムゾーンで解釈する、開始を受け付ける期限を表す。
	Deadline string `json:"start_deadline_at"`
	// Scheduled は予定時刻を表す。空の場合は予定時刻の待機判定を省略する。
	Scheduled string `json:"scheduled_fire_time"`
	// Cancelled はキャンセル要求時刻を表し、空文字列は未要求を意味する。
	Cancelled string `json:"cancel_requested_at"`
	// Grace は強制停止へ切り替えるまでの猶予秒数を表す。
	Grace int `json:"cancel_grace_seconds"`
	// RetryInterval は完了結果を再送する間隔を秒単位で表す。
	RetryInterval int `json:"result_retry_interval_seconds"`
	// RetryCount は旧Backendとの受信互換用の値であり、結果再送の停止には使用しない。
	RetryCount int `json:"result_retry_count"`
	// RetentionDays は旧Backendとの受信互換用の値であり、未受理結果の削除には使用しない。
	RetentionDays int `json:"unsent_result_retention_days"`
}

// result は成否に加え、未開始・停止確認不能を区別してBackendへ報告する。
type result struct {
	// Status は実行結果コードを表す。unknownは確定した完了結果として扱わない。
	Status string `json:"execution_status"`
	// ExitCode はOS終了コードを表し、未開始・取得不能時は他のフラグも併せて解釈する。
	ExitCode int `json:"exit_code"`
	// Stdout は上限付きで収集し、UTF-8とNULを正規化した標準出力を保持する。
	Stdout string `json:"stdout"`
	// Stderr は標準出力とは別の上限で収集した標準エラーを保持する。
	Stderr string `json:"stderr"`
	// Truncated は標準出力または標準エラーが保存上限を超えたことを表す。
	Truncated bool `json:"output_truncated"`
	// StopConfirmed は実行対象が動作していないことを確認できたかどうかを表す。
	StopConfirmed bool `json:"stop_confirmed"`
	// NotStarted はコマンドを開始する前に要求が終了したことを表す。
	NotStarted bool `json:"not_started"`
	// Message は準備失敗や確認不能などの補足情報を保持する。
	Message string `json:"message"`
}

// journal は再起動後の二重実行を防ぎ、実行意図・結果送信・作業領域の回収状況を永続化する。
type journal struct {
	// WorkStopped は停止確認済みまたは未開始の確定結果として回収対象になったことを表す。
	WorkStopped bool `json:"work_stopped,omitempty"`
	// CleanupAfter は作業領域を削除できる端末時刻を表す。ゼロ値は削除対象外を意味する。
	CleanupAfter time.Time `json:"cleanup_after,omitempty"`
	// WorkCleaned は作業領域の削除完了を保存したことを表す。
	WorkCleaned bool `json:"work_cleaned,omitempty"`
	// AgentID はこの記録を作成したAgentの識別子を表す。
	AgentID string `json:"agent_id"`
	// Generation は別世代の記録を送信・実行しないための登録世代を表す。
	Generation int64 `json:"generation"`
	// Execution は受信時点の実行条件を保持する。
	Execution execution `json:"execution"`
	// Phase は端末内の処理段階であり、Backendの実行状態とは区別する。
	Phase string `json:"phase"`
	// StartedAt は保存形式上の開始時刻欄を表す。現在の常駐処理では設定しない。
	StartedAt string `json:"started_at,omitempty"`
	// Result は未送信の確定結果を保持する。結果不明・送信確定後はnilとなる。
	Result *result `json:"result,omitempty"`
	// CompletedAt は端末で結果を受領した時刻を表す。未受理結果の削除期限には使用しない。
	CompletedAt time.Time `json:"completed_at,omitempty"`
	// Attempts は永続化済みの結果送信試行回数を表す。
	Attempts int `json:"attempts"`
	// NextAttempt は結果送信を再び試行できる最短の端末時刻を表す。
	NextAttempt time.Time `json:"next_attempt,omitempty"`
}

// runtimeConfig は端末の実行環境を保持し、空のシェルはBackend設定で補完する。
type runtimeConfig struct {
	// Server は保存済み資格情報と一致する必要がある接続先の上書き値を表す。
	Server string `json:"server"`
	// Shell はコマンド実行に使用するシェルの実行ファイル名・パスを表す。
	Shell string `json:"shell"`
	// ShellArgs はコマンド本体の前に付けるシェル引数を表す。
	ShellArgs []string `json:"shell_args"`
	// WorkingDirectory は要求側に指定がない場合の作業ディレクトリを表す。
	WorkingDirectory string `json:"working_directory"`
}

// atomicJSON は準備済みの保存先へJSONを権限制限・同期・置換の順で確定する。
// nameは呼び出し元が検証したファイル名とする。内部の一時ファイルは解放・削除を試みる。
// deferでの後始末は最善努力とし、書込・同期・明示Closeの失敗を呼び出し元へ返す。
// 置換後の同期でも失敗する場合があるため、エラーだけで未保存とは断定できない。
func atomicJSON(dir, name string, value any) error {
	data, err := json.MarshalIndent(value, "", "  ")
	if err != nil {
		return err
	}
	f, err := os.CreateTemp(dir, ".runtime-*")
	if err != nil {
		return err
	}
	defer os.Remove(f.Name())
	defer f.Close()
	if err = securePath(f.Name(), false); err != nil {
		return err
	}
	if _, err = f.Write(data); err != nil {
		return err
	}
	if err = f.Sync(); err != nil {
		return err
	}
	if err = f.Close(); err != nil {
		return err
	}
	if err = replaceFile(f.Name(), filepath.Join(dir, name)); err != nil {
		return err
	}
	return syncDirectory(dir)
}

// validExecutionID はファイル名にも使用できる小文字16進UUIDの書式だけを許可する。
func validExecutionID(id string) bool {
	if len(id) != 36 {
		return false
	}
	for i, c := range id {
		if i == 8 || i == 13 || i == 18 || i == 23 {
			if c != '-' {
				return false
			}
		} else if !(c >= '0' && c <= '9' || c >= 'a' && c <= 'f') {
			return false
		}
	}
	return true
}

// loadJournals はJSONを復元し、開始途中・実行中の記録を結果不明として扱う。
// 破損・識別子不一致・読取失敗時は全体をエラーとし、自動上書きや再実行を行わない。
func loadJournals(dir string) (map[string]*journal, error) {
	entries, err := os.ReadDir(dir)
	if err != nil {
		return nil, err
	}
	values := map[string]*journal{}
	for _, entry := range entries {
		if filepath.Ext(entry.Name()) != ".json" {
			continue
		}
		info, err := entry.Info()
		if err != nil || !info.Mode().IsRegular() {
			return nil, errors.New("実行記録ファイルが不正です")
		}
		raw, err := os.ReadFile(filepath.Join(dir, entry.Name()))
		if err != nil {
			return nil, err
		}
		var item journal
		if json.Unmarshal(raw, &item) != nil || !validExecutionID(item.Execution.ID) || entry.Name() != item.Execution.ID+".json" {
			return nil, errors.New("実行記録が破損しています。自動上書き・再実行はしません")
		}

		// 開始意図の保存だけではOSプロセスの実際の開始・終了を証明できない。
		if item.Phase == phaseStarting || item.Phase == phaseRunning {
			item.Phase = phaseUnknown
		}
		values[item.Execution.ID] = &item
	}
	return values, nil
}

// terminal は確定結果として扱う状態だけを判定し、unknownは完了扱いにしない。
func terminal(status string) bool {
	switch status {
	case executionSuccess, executionFailed, executionTimeout, executionCancelled, executionExpired:
		return true
	}
	return false
}
