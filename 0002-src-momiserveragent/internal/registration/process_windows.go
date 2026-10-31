//go:build windows

package registration

import (
	"errors"
	"golang.org/x/sys/windows"
	"os/exec"
	"syscall"
	"unsafe"
)

// defaultShell はWindows既定のcmd.exeと、AutoRun無効化を含む引数を返す。
func defaultShell() (string, []string) { return "cmd.exe", []string{"/D", "/S", "/C"} }

// startManaged はプロセスを一時停止状態で開始し、子孫を管理するJobへ割り当てて再開する。
// 失敗時は開始したプロセスの終了とハンドル解放を試み、元のエラーを返す。
// 成功時の第1関数は通常停止・強制停止を切り替え、第2関数はJobを解放する。
// 呼び出し元はcmd.Waitで終了を回収し、管理終了時に第2関数を呼ぶ。
func startManaged(cmd *exec.Cmd) (func(bool) error, func(), error) {
	job, err := windows.CreateJobObject(nil, nil)
	if err != nil {
		return nil, nil, err
	}
	closeJob := func() { windows.CloseHandle(job) }
	limits := windows.JOBOBJECT_EXTENDED_LIMIT_INFORMATION{}
	limits.BasicLimitInformation.LimitFlags = windows.JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE
	_, err = windows.SetInformationJobObject(job, windows.JobObjectExtendedLimitInformation, uintptr(unsafe.Pointer(&limits)), uint32(unsafe.Sizeof(limits)))
	if err != nil {
		closeJob()
		return nil, nil, err
	}
	cmd.SysProcAttr = &syscall.SysProcAttr{CreationFlags: windows.CREATE_SUSPENDED | windows.CREATE_NEW_PROCESS_GROUP, HideWindow: true}
	if err = cmd.Start(); err != nil {
		closeJob()
		return nil, nil, err
	}
	failed := func(err error) (func(bool) error, func(), error) {
		_ = cmd.Process.Kill()
		_ = cmd.Wait()
		closeJob()
		return nil, nil, err
	}
	process, err := windows.OpenProcess(windows.PROCESS_SET_QUOTA|windows.PROCESS_TERMINATE, false, uint32(cmd.Process.Pid))
	if err != nil {
		return failed(err)
	}
	err = windows.AssignProcessToJobObject(job, process)
	windows.CloseHandle(process)
	if err != nil {
		return failed(err)
	}
	snapshot, err := windows.CreateToolhelp32Snapshot(windows.TH32CS_SNAPTHREAD, 0)
	if err != nil {
		return failed(err)
	}
	entry := windows.ThreadEntry32{Size: uint32(unsafe.Sizeof(windows.ThreadEntry32{}))}
	resumed := false
	for err = windows.Thread32First(snapshot, &entry); err == nil; err = windows.Thread32Next(snapshot, &entry) {
		if entry.OwnerProcessID != uint32(cmd.Process.Pid) {
			continue
		}
		thread, openErr := windows.OpenThread(windows.THREAD_SUSPEND_RESUME, false, entry.ThreadID)
		if openErr == nil {
			_, resumeErr := windows.ResumeThread(thread)
			windows.CloseHandle(thread)
			resumed = resumeErr == nil
		}
		break
	}
	windows.CloseHandle(snapshot)
	if !resumed {
		return failed(errors.New("cannot resume managed process"))
	}
	stop := func(force bool) error {
		if !force {
			return windows.GenerateConsoleCtrlEvent(windows.CTRL_BREAK_EVENT, uint32(cmd.Process.Pid))
		}
		return windows.TerminateJobObject(job, 1)
	}
	return stop, closeJob, nil
}
