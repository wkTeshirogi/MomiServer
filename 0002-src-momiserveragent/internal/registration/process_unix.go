//go:build linux || darwin

package registration

import (
	"errors"
	"os/exec"
	"syscall"
)

// defaultShell はUnix系OS既定の/bin/shとコマンド実行用引数を返す。
func defaultShell() (string, []string) { return "/bin/sh", []string{"-c"} }

// startManaged は専用プロセスグループで開始し、グループ単位の停止関数を返す。
// 停止は通常SIGTERM、強制時SIGKILLとし、既に対象がないESRCHは成功扱いにする。
// 開始失敗はエラーを返す。成功時は呼び出し元がcmd.Waitで回収し、第2の解放関数も呼ぶ。
func startManaged(cmd *exec.Cmd) (func(bool) error, func(), error) {
	cmd.SysProcAttr = &syscall.SysProcAttr{Setpgid: true}
	if err := cmd.Start(); err != nil {
		return nil, nil, err
	}
	stop := func(force bool) error {
		sig := syscall.SIGTERM
		if force {
			sig = syscall.SIGKILL
		}
		err := syscall.Kill(-cmd.Process.Pid, sig)
		if errors.Is(err, syscall.ESRCH) {
			return nil
		}
		return err
	}
	return stop, func() {}, nil
}
