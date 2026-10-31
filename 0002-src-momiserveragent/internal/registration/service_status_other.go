//go:build !windows && !linux

package registration

import (
	"context"
	"errors"
)

// collectServiceRuntime はこのOSのサービス照会が未対応であることをエラーで返す。
func collectServiceRuntime(_ context.Context, _ string) (serviceRuntime, error) {
	return serviceRuntime{}, errors.New("このOSのサービス管理機構には対応していません")
}
