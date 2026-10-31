package registration

import (
	"bytes"
	"context"
	"encoding/json"
	"os"
	"os/exec"
	"strings"
	"time"
)

// boundedOutput は先頭64KiBだけを保持し、超過後も出力を消費して出力待ちによる停止を防ぐ。
// 標準出力・標準エラーで別々に使用し、同じバッファを並行操作しない。
type boundedOutput struct {
	// Buffer は上限内の出力だけを保持する。書き込みには上限を適用するWriteを使う。
	bytes.Buffer
	truncated bool
}

// Write は保存上限を超える部分を破棄し、入力全体を消費したバイト数を返す。
// 切り捨て時はtruncatedを記録する。書き込みエラーは返さず、出力パイプを詰まらせない。
func (b *boundedOutput) Write(p []byte) (int, error) {
	// nは切り捨て後の保存量ではなく、Writerへ渡された入力全体の消費量を表す。
	n := len(p)
	left := 65536 - b.Len()
	if left < len(p) {
		p = p[:left]
		b.truncated = true
	}
	_, _ = b.Buffer.Write(p)
	return n, nil
}

// executeProcess はシェルと子孫プロセスを監視し、期限・キャンセル・Agent終了に応じて停止する。
// ctx終了は結果不明、cancelの受信はキャンセルとして扱い、停止確認不能時もunknownにする。
// Wait用goroutineは終了確認後に1件だけ通知する。停止確認不能で先に戻る場合は待機が残り得る。
// タイマーとOS管理資源は内部で解放する。
// 出力は各64KiBまで保持する。パラメータはシェル文字列ではなくJSON環境変数として渡す。
func executeProcess(ctx context.Context, e execution, config runtimeConfig, cancel <-chan struct{}) result {
	shell, args := defaultShell()
	if config.Shell != "" {
		shell = config.Shell
		args = config.ShellArgs
	}
	cmd := exec.Command(shell, append(append([]string{}, args...), e.Command)...)
	cmd.WaitDelay = 3 * time.Second
	parameters, err := json.Marshal(e.Parameters)
	if err != nil {
		return result{Status: executionFailed, NotStarted: true, Message: "パラメータを構成できません"}
	}

	// 入力値をシェル構文へ展開せず、構造化した環境変数として渡す。
	cmd.Env = append(os.Environ(), "MOMISERVER_PARAMETERS_JSON="+string(parameters), "MOMISERVER_EXECUTION_ID="+e.ID)
	cmd.Dir = e.Directory
	if cmd.Dir == "" {
		cmd.Dir = config.WorkingDirectory
	}
	var stdout, stderr boundedOutput
	cmd.Stdout = &stdout
	cmd.Stderr = &stderr
	stop, closeProcess, err := startManaged(cmd)
	if err != nil {
		return result{Status: executionFailed, ExitCode: -1, NotStarted: true, Message: "プロセスを開始できません"}
	}
	defer closeProcess()
	// doneは関数が先に戻ってもWaitの送信を妨げない、1件分の終了通知を保持する。
	done := make(chan error, 1)
	go func() { done <- cmd.Wait() }()
	timer := time.NewTimer(time.Duration(e.Timeout) * time.Second)
	defer timer.Stop()
	status := executionSuccess
	var waitErr error
	stopped := false
	select {
	case waitErr = <-done:
		// 親シェルが終了しても残った子孫を停止してから完了を確定する。
		stopped = stop(true) == nil
	case <-timer.C:
		status = executionTimeout
	case <-cancel:
		status = executionCancelled
	case <-ctx.Done():
		status = executionUnknown
	}
	if status != executionSuccess {
		// 通常停止の失敗だけで終了せず、猶予後の強制停止結果で停止可否を判断する。
		_ = stop(false)
		grace := time.NewTimer(time.Duration(e.Grace) * time.Second)
		select {
		case waitErr = <-done:
			stopped = stop(true) == nil
		case <-grace.C:
			stopped = stop(true) == nil
			select {
			case waitErr = <-done:
			case <-time.After(5 * time.Second):
				return result{Status: executionUnknown, Message: "プロセスの停止を確認できません"}
			}
		}
		grace.Stop()
	}
	code := 0
	if waitErr != nil {
		code = -1
		if cmd.ProcessState != nil {
			code = cmd.ProcessState.ExitCode()
		}
	}
	if status == executionSuccess && code != 0 {
		status = executionFailed
	}

	// 終了コードが0でも子孫プロセスの停止を確認できなければ成功扱いにせず、運用確認が必要な結果として残す。
	if !stopped {
		status = executionUnknown
	}
	return result{Status: status, ExitCode: code, StopConfirmed: stopped,
		Stdout: strings.ReplaceAll(strings.ToValidUTF8(stdout.String(), "�"), "\x00", "�"), Stderr: strings.ReplaceAll(strings.ToValidUTF8(stderr.String(), "�"), "\x00", "�"),
		Truncated: stdout.truncated || stderr.truncated}
}
