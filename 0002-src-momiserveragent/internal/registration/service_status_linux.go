//go:build linux

package registration

import (
	"context"
	"errors"
	"os/exec"
	"strconv"
	"strings"
)

// collectServiceRuntime はsystemdのサービス名を照会し、共通状態とMainPIDを返す。
// ctxでsystemctlを中断できる。コマンド失敗・PID不正はエラーにし、未知の状態はunknownで返す。
func collectServiceRuntime(ctx context.Context, name string) (serviceRuntime, error) {
	output, err := exec.CommandContext(ctx, "systemctl", "show", name,
		"--property=ActiveState", "--property=MainPID", "--no-pager").Output()
	if err != nil {
		return serviceRuntime{}, err
	}
	properties := make(map[string]string)
	for _, line := range strings.Split(string(output), "\n") {
		key, value, found := strings.Cut(line, "=")
		if found {
			properties[key] = value
		}
	}
	result := serviceRuntime{status: runtimeUnknown}
	switch properties["ActiveState"] {
	case "active":
		result.status = runtimeOnline
	case "inactive", "failed":
		result.status = runtimeOffline
	}
	if rawPID := properties["MainPID"]; rawPID != "" && rawPID != "0" {
		pid, parseErr := strconv.ParseInt(rawPID, 10, 32)
		if parseErr != nil {
			return serviceRuntime{}, errors.New("systemdのMainPIDが不正です")
		}
		result.pid = int32(pid)
	}
	return result, nil
}
