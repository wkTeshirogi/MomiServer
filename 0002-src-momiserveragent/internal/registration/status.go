package registration

import (
	"context"
	"errors"
	"math"
	"strings"
	"time"

	"github.com/shirou/gopsutil/v4/cpu"
	"github.com/shirou/gopsutil/v4/disk"
	"github.com/shirou/gopsutil/v4/host"
	"github.com/shirou/gopsutil/v4/mem"
	"github.com/shirou/gopsutil/v4/process"
	"github.com/shirou/gopsutil/v4/sensors"
)

// serviceTarget はBackendから受け取る監視対象サービスの識別情報を表す。
type serviceTarget struct {
	// ID は取得結果を紐づけるサービスマスタの識別子を表す。
	ID int64 `json:"id"`
	// Name はOSサービス管理機構へ問い合わせるサービス名を表す。
	Name string `json:"service_name"`
}

// statusTargets はこのAgentが監視するサービス一覧の応答を表す。
type statusTargets struct {
	// Services は端末に紐づく監視対象一覧を表す。空でも端末実測は取得する。
	Services []serviceTarget `json:"services"`
}

// storageSnapshot は一つのマウントポイントの容量と表示情報を保持する。
type storageSnapshot struct {
	// Key は重複を除いて識別するマウントポイントを表す。
	Key string `json:"storage_key"`
	// DisplayName はデバイス名を補足した表示名を表す。
	DisplayName string `json:"display_name"`
	// TotalBytes は領域の総容量をバイト単位で表す。
	TotalBytes uint64 `json:"total_bytes"`
	// UsedBytes は使用済み容量をバイト単位で表す。
	UsedBytes uint64 `json:"used_bytes"`
}

// serviceSnapshot は実測したサービス状態と取得できたプロセス指標を保持する。
type serviceSnapshot struct {
	// ServiceID は監視対象のサービスマスタ識別子を表す。
	ServiceID int64 `json:"service_id"`
	// Status はonline・offline・unknownの共通状態コードを表す。
	Status string `json:"status"`
	// ProcessID は実測したPIDを表す。未取得はnilでJSONから省略する。
	ProcessID *int64 `json:"process_id,omitempty"`
	// Uptime はプロセスの稼働秒数を表す。取得不能の場合は0のままとする。
	Uptime uint64 `json:"uptime_seconds"`
	// CPUPercent はCPU時間の差分から算出し、0～100へ丸めた使用率を表す。
	CPUPercent float64 `json:"cpu_percent"`
	// MemoryPercent は0～100へ丸めたプロセスのメモリ使用率を表す。
	MemoryPercent float64 `json:"memory_percent"`
}

// statusSnapshot は端末実測とサービス状態を同じ送信単位で保持する。
type statusSnapshot struct {
	// CollectedAt は端末実測を組み立てたUTC時刻を表す。
	CollectedAt time.Time `json:"collected_at"`
	// CPUPercent は端末全体のCPU使用率を0～100で表す。
	CPUPercent float64 `json:"cpu_percent"`
	// MemoryPercent は端末全体のメモリ使用率を0～100で表す。
	MemoryPercent float64 `json:"memory_percent"`
	// CPUTemperature はCPU温度の最大取得値を摂氏で表す。未取得はnilとする。
	CPUTemperature *float64 `json:"cpu_temperature_c,omitempty"`
	// GPUTemperature はGPU温度の最大取得値を摂氏で表す。未取得はnilとする。
	GPUTemperature *float64 `json:"gpu_temperature_c,omitempty"`
	// Uptime は端末の起動からの稼働秒数を表す。
	Uptime uint64 `json:"uptime_seconds"`
	// Storage は取得可能なマウントポイントの実測一覧を表す。
	Storage []storageSnapshot `json:"storage"`
	// Services は監視対象ごとの取得結果を表し、個別の取得失敗はunknownなどで表現する。
	Services []serviceSnapshot `json:"services"`
}

// statusCollector はctxに従って端末実測を収集し、送信可能なスナップショットを返す。
type statusCollector func(context.Context, []serviceTarget) (statusSnapshot, error)

// serviceRuntime はOSサービス状態を共通コードとPIDへ正規化した結果を保持する。
type serviceRuntime struct {
	status string
	pid    int32
}

// serviceProcessSample はサービスCPU使用率の差分計算に必要な初回観測を保持する。
type serviceProcessSample struct {
	snapshot     serviceSnapshot
	process      *process.Process
	initialTimes *cpu.TimesStat
	sampledAt    time.Time
}

// collectRuntimeStatus は端末全体とサービスの実測を収集し、UTC時刻付きで返す。
// CPU・メモリ・稼働時間の取得失敗は全体をエラーにし、温度・ストレージ・個別サービスは取得可能分を返す。
// ctxは対応するOS取得APIへ渡す。温度取得APIはcontextを受け付けない。
func collectRuntimeStatus(ctx context.Context, targets []serviceTarget) (statusSnapshot, error) {
	serviceSamples := make([]serviceProcessSample, 0, len(targets))
	for _, target := range targets {
		serviceSamples = append(serviceSamples, startServiceSample(ctx, target))
	}

	// 端末CPUの観測待ちをサービスCPUの差分観測にも使い、各サービスごとに待機時間を追加しない。
	values, err := cpu.PercentWithContext(ctx, 250*time.Millisecond, false)
	if err != nil || len(values) != 1 {
		return statusSnapshot{}, errors.New("CPU使用率を取得できません")
	}
	memory, err := mem.VirtualMemoryWithContext(ctx)
	if err != nil {
		return statusSnapshot{}, errors.New("メモリ使用率を取得できません")
	}
	uptime, err := host.UptimeWithContext(ctx)
	if err != nil {
		return statusSnapshot{}, errors.New("端末稼働時間を取得できません")
	}
	cpuTemperature, gpuTemperature := temperatures()
	snapshot := statusSnapshot{
		CollectedAt:    time.Now().UTC(),
		CPUPercent:     clampPercent(values[0]),
		MemoryPercent:  clampPercent(memory.UsedPercent),
		CPUTemperature: cpuTemperature,
		GPUTemperature: gpuTemperature,
		Uptime:         uptime,
		Storage:        collectStorage(ctx),
		Services:       make([]serviceSnapshot, 0, len(serviceSamples)),
	}
	for _, sample := range serviceSamples {
		snapshot.Services = append(snapshot.Services, finishServiceSample(ctx, sample))
	}
	return snapshot, nil
}

// collectStorage は重複マウントと不正な容量値を除き、取得可能なストレージだけを返す。
// 一覧取得失敗は空配列、個別取得失敗は該当領域の省略として扱う。
func collectStorage(ctx context.Context) []storageSnapshot {
	partitions, err := disk.PartitionsWithContext(ctx, false)
	if err != nil {
		return []storageSnapshot{}
	}
	result := make([]storageSnapshot, 0, len(partitions))
	seen := make(map[string]bool)
	for _, partition := range partitions {
		if partition.Mountpoint == "" || seen[partition.Mountpoint] {
			continue
		}
		usage, err := disk.UsageWithContext(ctx, partition.Mountpoint)
		if err != nil || usage.Total == 0 || usage.Used > usage.Total {
			continue
		}
		seen[partition.Mountpoint] = true
		name := partition.Mountpoint
		if partition.Device != "" && !strings.EqualFold(partition.Device, partition.Mountpoint) {
			name = partition.Device + " (" + partition.Mountpoint + ")"
		}
		result = append(result, storageSnapshot{partition.Mountpoint, name, usage.Total, usage.Used})
	}
	return result
}

// startServiceSample はサービス状態・PID・CPU時間の初回観測を最大5秒のcontextで試みる。
// 失敗した項目は取得可能分だけ保持し、状態照会失敗はunknownとして返す。
func startServiceSample(ctx context.Context, target serviceTarget) serviceProcessSample {
	result := serviceProcessSample{snapshot: serviceSnapshot{ServiceID: target.ID, Status: runtimeUnknown}}
	serviceCtx, cancel := context.WithTimeout(ctx, 5*time.Second)
	defer cancel()
	runtimeStatus, err := collectServiceRuntime(serviceCtx, target.Name)
	if err != nil {
		return result
	}
	result.snapshot.Status = runtimeStatus.status
	if runtimeStatus.pid <= 0 {
		return result
	}
	pid := int64(runtimeStatus.pid)
	result.snapshot.ProcessID = &pid
	processValue, err := process.NewProcessWithContext(serviceCtx, runtimeStatus.pid)
	if err != nil {
		return result
	}
	initialTimes, err := processValue.TimesWithContext(serviceCtx)
	if err != nil {
		return result
	}
	result.process = processValue
	result.initialTimes = initialTimes
	result.sampledAt = time.Now()
	return result
}

// finishServiceSample は初回観測との差分からプロセス指標を補完する。
// 最大5秒のcontextを内部で解除し、項目ごとの取得失敗では既存値を維持する。
func finishServiceSample(ctx context.Context, sample serviceProcessSample) serviceSnapshot {
	if sample.process == nil || sample.initialTimes == nil {
		return sample.snapshot
	}
	serviceCtx, cancel := context.WithTimeout(ctx, 5*time.Second)
	defer cancel()
	if finalTimes, err := sample.process.TimesWithContext(serviceCtx); err == nil {
		delta := (finalTimes.User - sample.initialTimes.User) + (finalTimes.System - sample.initialTimes.System)
		sample.snapshot.CPUPercent = processPercent(delta, time.Since(sample.sampledAt))
	}
	if value, err := sample.process.MemoryPercentWithContext(serviceCtx); err == nil {
		sample.snapshot.MemoryPercent = clampPercent(float64(value))
	}
	if createdMillis, err := sample.process.CreateTimeWithContext(serviceCtx); err == nil {
		createdAt := time.UnixMilli(createdMillis)
		if createdAt.Before(time.Now()) {
			sample.snapshot.Uptime = uint64(time.Since(createdAt).Seconds())
		}
	}
	return sample.snapshot

}

// processPercent は観測間のCPU使用秒数を経過秒数で割り、0～100へ丸める。
// 非正のCPU時間・経過時間は0とする。
func processPercent(cpuSeconds float64, elapsed time.Duration) float64 {
	if cpuSeconds <= 0 || elapsed <= 0 {
		return 0
	}
	return clampPercent(cpuSeconds / elapsed.Seconds() * 100)
}

// temperatures は取得可能なCPU・GPUセンサーの最大温度を摂氏で返す。
// センサー取得失敗・対象外しかない場合はnilとし、異常な温度値は除外する。
func temperatures() (*float64, *float64) {
	values, err := sensors.SensorsTemperatures()
	if err != nil {
		return nil, nil
	}
	var cpuValue, gpuValue *float64
	for _, value := range values {
		if value.Temperature < -100 || value.Temperature > 250 {
			continue
		}
		name := strings.ToLower(value.SensorKey)
		switch {
		case strings.Contains(name, "gpu"), strings.Contains(name, "nvidia"), strings.Contains(name, "amdgpu"):
			gpuValue = maxTemperature(gpuValue, value.Temperature)
		case strings.Contains(name, "cpu"), strings.Contains(name, "core"), strings.Contains(name, "package"):
			cpuValue = maxTemperature(cpuValue, value.Temperature)
		}
	}
	return cpuValue, gpuValue
}

// maxTemperature は未取得値または既存の最大値を更新する。currentがnilでも使用できる。
func maxTemperature(current *float64, value float64) *float64 {
	if current == nil || value > *current {
		result := value
		return &result
	}
	return current
}

// clampPercent は有限な値を0～100へ制限して小数2桁に丸め、NaN・無限大は0とする。
func clampPercent(value float64) float64 {
	if math.IsNaN(value) || math.IsInf(value, 0) {
		return 0
	}
	return math.Round(math.Max(0, math.Min(100, value))*100) / 100
}
