//go:build windows

package registration

import (
	"context"
	"errors"

	"golang.org/x/sys/windows"
	"golang.org/x/sys/windows/svc"
	"golang.org/x/sys/windows/svc/mgr"
)

// collectServiceRuntime は接続・状態照会の最小権限でWindowsサービスの状態とPIDを取得する。
// SCMとサービスのハンドルは内部で閉じる。照会失敗・未対応状態はエラーにする。
// Windows APIには渡されたcontextによる中断機構を設けていない。
func collectServiceRuntime(_ context.Context, name string) (serviceRuntime, error) {
	managerHandle, err := windows.OpenSCManager(nil, nil, windows.SC_MANAGER_CONNECT)
	if err != nil {
		return serviceRuntime{}, err
	}
	defer windows.CloseServiceHandle(managerHandle)
	namePointer, err := windows.UTF16PtrFromString(name)
	if err != nil {
		return serviceRuntime{}, err
	}
	serviceHandle, err := windows.OpenService(managerHandle, namePointer, windows.SERVICE_QUERY_STATUS)
	if err != nil {
		return serviceRuntime{}, err
	}
	service := &mgr.Service{Name: name, Handle: serviceHandle}
	defer service.Close()
	status, err := service.Query()
	if err != nil {
		return serviceRuntime{}, err
	}
	result := serviceRuntime{status: runtimeUnknown, pid: int32(status.ProcessId)}
	switch status.State {
	case svc.Running:
		result.status = runtimeOnline
	case svc.Stopped:
		result.status = runtimeOffline
	case svc.Paused, svc.StartPending, svc.StopPending, svc.ContinuePending, svc.PausePending:
		result.status = runtimeUnknown
	default:
		return serviceRuntime{}, errors.New("未対応のWindowsサービス状態です")
	}
	return result, nil
}
