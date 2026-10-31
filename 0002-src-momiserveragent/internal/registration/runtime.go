package registration

import (
	"context"
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"io"
	"os"
	"os/signal"
	"path/filepath"
	"syscall"
	"time"
	_ "time/tzdata"
)

// settings はBackendから取得し、妥当性検証後に保存するバッチ共通設定を保持する。
type settings struct {
	// Max は添付準備中を含む実行要求の同時処理上限を表す。
	Max int `json:"max_concurrent_executions"`
}

// completion は実行goroutineから常駐ループへ渡す完了通知を表す。
type completion struct {
	id    string
	value result
}

// activeProcess は常駐ループが所有するキャンセル通知と送信済みフラグを保持する。
// cancelは常駐ループだけが一度閉じ、実行goroutineは受信専用で参照する。
type activeProcess struct {
	cancel    chan struct{}
	cancelled bool
}

// daemon は単一の常駐ループが所有する状態を保持し、mapとstateを並行更新しない。
// 実行goroutineは完了をdoneへ通知し、MQTTコールバックはwakeで再照合を促す。
type daemon struct {
	dir, jobsDir                string
	state                       *State
	client                      *Client
	config                      runtimeConfig
	journals                    map[string]*journal
	active                      map[string]*activeProcess
	done                        chan completion
	settings                    settings
	broker                      *runtimeBroker
	output                      io.Writer
	lastHeartbeat, timeSettings time.Time
	heartbeatAttempts           int
	nextHeartbeat               time.Time
	statusCollector             statusCollector
	nextStatus                  time.Time
}

// runDaemon は保存先を排他利用し、HTTP照合・実測送信・バッチ処理を終了シグナルまで継続する。
// 起動時の設定・認証情報・ジャーナル不正はエラーとし、周期処理の失敗は次周期で再試行する。
// 終了時はcontextをキャンセルし、開始したgoroutineを回収してからMQTTとロックを解放する。
// outputは呼び出し元が管理し、ここでは閉じない。
func runDaemon(args []string, output io.Writer) error {
	flags := flag.NewFlagSet("run", flag.ContinueOnError)
	flags.SetOutput(io.Discard)
	directory := flags.String("state-dir", defaultDirectory(), "state directory")
	allowHTTP := flags.Bool("allow-insecure-localhost", false, "local HTTP development")
	allowMQTT := flags.Bool("allow-insecure-mqtt-localhost", false, "local MQTT development")
	retryResults := flags.Bool("retry-results", false, "explicitly retry retained completed results; never rerun processes")
	if flags.Parse(args) != nil || flags.NArg() != 0 {
		return errors.New("runの引数を確認してください")
	}
	dir, err := filepath.Abs(*directory)
	if err != nil {
		return err
	}
	if err = prepareDirectory(dir); err != nil {
		return err
	}
	// unlockは保存先全体の排他ロックを保持し、実行中のCLIによる同時更新を防ぐ。
	unlock, err := lockDirectory(dir)
	if err != nil {
		return err
	}
	defer unlock()
	state, err := loadState(dir)
	if err != nil {
		return err
	}
	if state == nil || state.Tokens == nil {
		return errors.New("先にAgentを登録してください")
	}
	config := runtimeConfig{}
	raw, err := os.ReadFile(filepath.Join(dir, "config.json"))
	if err == nil {
		if json.Unmarshal(raw, &config) != nil {
			return errors.New("config.jsonが不正です")
		}
	} else if !os.IsNotExist(err) {
		return err
	}
	server := state.Server
	if config.Server != "" {
		server = config.Server
	}
	resolved, err := serverURLWithOptions(server, *allowHTTP)
	if err != nil {
		return err
	}
	if resolved != state.Server {
		return errors.New("保存済みの接続先と異なります")
	}
	jobsDir := filepath.Join(dir, "executions")
	if err = prepareDirectory(jobsDir); err != nil {
		return err
	}
	journals, err := loadJournals(jobsDir)
	if err != nil {
		return err
	}
	// 明示指定は再送待機だけを解除する。監査用試行回数とコマンドの再実行防止記録は維持する。
	if *retryResults {
		for _, j := range journals {
			if j.Phase == phaseCompleted && j.Result != nil && j.AgentID == state.Tokens.AgentID && j.Generation == state.Tokens.Generation {
				j.NextAttempt = time.Time{}
				if err := atomicJSON(jobsDir, j.Execution.ID+".json", j); err != nil {
					return err
				}
			}
		}
	}
	// dの状態はこのループで更新し、実行goroutineからは完了通知だけを受け取る。
	d := &daemon{dir: dir, jobsDir: jobsDir, state: state, client: NewClient(resolved), config: config,
		journals: journals, active: map[string]*activeProcess{}, done: make(chan completion, 1024), output: output,
		statusCollector: collectRuntimeStatus}
	d.broker = newRuntimeBroker(*allowMQTT)
	defer d.broker.close()
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()
	// 途中エラーでも実行goroutineを回収する。保存失敗時は記録確認を促して回収を継続する。
	defer func() {
		stop()
		for len(d.active) > 0 {
			c := <-d.done
			if err := d.finished(c); err != nil {
				delete(d.active, c.id)
				fmt.Fprintln(output, "実行結果の保存に失敗しました。実行記録を確認してください")
			}
		}
	}()
	fmt.Fprintln(output, "Agent常駐処理を開始しました。終了: Ctrl+C")
	ticker := time.NewTicker(10 * time.Second)
	defer ticker.Stop()

	// 同期失敗で新規開始を止めても、開始済み処理はローカルの制限時間で監視し続ける。
	cycle := func() {
		if err := d.cleanupWork(time.Now()); err != nil {
			fmt.Fprintln(output, "作業領域の回収を再試行します")
		}
		if err := d.cycle(ctx); err != nil {
			fmt.Fprintln(output, "同期待ち: バックエンド認証・接続・設定を確認してください")
		}
	}
	cycle()
	for {
		select {
		case <-ctx.Done():
			for len(d.active) > 0 {
				c := <-d.done
				if err := d.finished(c); err != nil {
					return err
				}
			}
			return nil
		case c := <-d.done:
			if err := d.finished(c); err != nil {
				return err
			}
			cycle()
		case <-ticker.C:
			cycle()
		case <-d.broker.wake:
			cycle()
		}
	}
}

// save は検証済みの実行IDをファイル名にしてジャーナルを永続化する。
func (d *daemon) save(j *journal) error { return atomicJSON(d.jobsDir, j.Execution.ID+".json", j) }

// api は必要な資格情報更新を先に確定し、現在のトークンでAgent APIを呼び出す。
// 要求IDは送信前、新資格情報は利用開始前に保存する。常駐ループからだけ呼ぶ。
// HTTP通信はrequestの期限に従い、contextキャンセルでは直接中断しない。
func (d *daemon) api(method, path string, body, target any) error {
	if d.state.RefreshRequestID != "" || time.Until(d.state.Tokens.AccessExpiresAt) < accessRefreshLead {

		// 応答喪失や途中停止の後も同じ更新要求として照合できるよう、要求IDは送信より先に確定させる。
		if d.state.RefreshRequestID == "" {
			var err error
			d.state.RefreshRequestID, err = newID()
			if err != nil {
				return err
			}
			if err = saveState(d.dir, d.state); err != nil {
				return err
			}
		}
		next, err := d.client.Refresh(d.state)
		if err != nil {
			return err
		}
		updated := *d.state
		updated.Tokens = next
		updated.RefreshRequestID = ""

		// 保存に失敗した場合はメモリ上も旧状態を維持し、未保存の新資格情報だけで運用を続けない。
		if err = saveState(d.dir, &updated); err != nil {
			return err
		}
		d.state = &updated
	}
	return d.client.request(method, path, d.state.Tokens.AccessToken, body, target)
}

// cycle は設定・実測値・実行状態をBackendと照合し、実行可能な未開始要求を起動する。
// 開始意図の保存とBackendの受理確認が先行し、結果不明の要求は再実行しない。
// 常駐ループから直列に呼び、実測収集と実行goroutineへctxを渡し、完了をdoneで回収する。
// 認証・設定・保存・照合の失敗はエラーにし、呼び出し元が次周期の再試行を判断する。
func (d *daemon) cycle(ctx context.Context) error {
	if ctx.Err() != nil {
		return nil
	}

	// 設定・MQTTの取得が失敗しても、既に保存した結果の受理確認を先に試す。
	var deliveryErr error
	for _, j := range d.journals {
		if j.AgentID != d.state.Tokens.AgentID || j.Generation != d.state.Tokens.Generation {
			continue
		}
		if err := d.deliver(j); err != nil && deliveryErr == nil {
			deliveryErr = err
		}
	}
	if deliveryErr != nil {
		return deliveryErr
	}
	if time.Since(d.timeSettings) > time.Minute {
		var s settings
		if err := d.api("GET", "/api/v1/agent/batch/settings", nil, &s); err != nil {
			return err
		}
		if s.Max < 1 || s.Max > 1000 {
			return errors.New("invalid concurrency")
		}
		if err := atomicJSON(d.dir, "batch-settings.json", s); err != nil {
			return err
		}
		d.settings = s
		if d.config.Shell == "" {
			var environment runtimeConfig
			if err := d.api("GET", "/api/v1/agent/runtime/environment", nil, &environment); err != nil {
				return err
			}
			if environment.Shell == "" {
				return errors.New("execution environment unavailable")
			}
			if err := atomicJSON(d.dir, "execution-environment.json", environment); err != nil {
				return err
			}
			d.config.Shell = environment.Shell
			d.config.ShellArgs = environment.ShellArgs
			if d.config.WorkingDirectory == "" {
				d.config.WorkingDirectory = environment.WorkingDirectory
			}
		}
		d.timeSettings = time.Now()
	}
	var connection mqttConnection
	if err := d.api("GET", "/api/v1/agent/mqtt/settings", nil, &connection); err != nil {
		d.broker.close()
		return err
	}
	if connection.Enabled && d.broker.settings != connection {
		if err := atomicJSON(d.dir, "mqtt-settings.json", connection); err != nil {
			return err
		}
	}
	if err := d.broker.configure(connection); err != nil {
		fmt.Fprintln(d.output, "MQTT再接続待ち。HTTPで実行状態を照合します")
	}
	if !time.Now().Before(d.nextHeartbeat) {
		if err := d.api("POST", "/api/v1/agent/heartbeat", map[string]bool{"mqtt_connected": d.broker.connected()}, nil); err != nil {
			d.heartbeatAttempts++
			d.nextHeartbeat = time.Now().Add(10 * time.Second)
			if d.heartbeatAttempts >= 4 {
				d.heartbeatAttempts = 0
				d.nextHeartbeat = time.Now().Add(time.Minute)
			}
		} else {
			d.heartbeatAttempts = 0
			d.lastHeartbeat = time.Now()
			d.nextHeartbeat = time.Now().Add(time.Minute)
		}
	}
	if d.statusCollector != nil && !time.Now().Before(d.nextStatus) {
		if err := d.publishStatus(ctx); err != nil {
			fmt.Fprintln(d.output, "端末・サービス状態の送信を再試行します")
			d.nextStatus = time.Now().Add(10 * time.Second)
		} else {
			d.nextStatus = time.Now().Add(time.Minute)
		}
	}

	var sync struct {
		IDs []string `json:"execution_ids"`
	}
	if err := d.api("GET", "/api/v1/agent/runtime/sync", nil, &sync); err != nil {
		return err
	}
	for _, id := range sync.IDs {
		if !validExecutionID(id) {
			continue
		}
		var envelope struct {
			AgentID    string    `json:"agent_id"`
			Generation int64     `json:"generation"`
			ServerTime time.Time `json:"server_time"`
			TimeZone   string    `json:"time_zone"`
			Execution  execution `json:"execution"`
		}
		if err := d.api("GET", "/api/v1/agent/batch/executions/"+id+"/command", nil, &envelope); err != nil {
			return err
		}
		if envelope.AgentID != d.state.Tokens.AgentID || envelope.Generation != d.state.Tokens.Generation || envelope.Execution.ID != id {
			return errors.New("execution identity mismatch")
		}
		e := envelope.Execution
		if a := d.active[id]; a != nil {
			if e.Cancelled != "" && !a.cancelled {
				close(a.cancel)
				a.cancelled = true
			}
			continue
		}

		// 完了済み・結果不明の記録も再実行防止に使う。HTTP再送やMQTTの重複通知で同じプロセスを起動し直さない。
		if j := d.journals[id]; j != nil {
			continue
		}
		if terminal(e.Status) {
			continue
		}

		// Backendが実行中でもローカル記録がない場合は、二重実行を避けて結果不明として保存する。
		if e.Status == executionRunning {
			j := &journal{AgentID: envelope.AgentID, Generation: envelope.Generation, Execution: e, Phase: phaseUnknown}
			if err := d.save(j); err != nil {
				return err
			}
			d.journals[id] = j
			continue
		}
		if !connection.Enabled || d.broker.settings != connection || len(d.active) >= d.settings.Max || d.hasPendingResults() {
			continue
		}
		if e.Timeout < 1 || e.Grace < 0 || e.RetryInterval < 1 || e.Command == "" {
			return errors.New("invalid execution settings")
		}
		loc, err := time.LoadLocation(envelope.TimeZone)
		if err != nil {
			return errors.New("server time zone unavailable")
		}
		deadline, err := time.ParseInLocation("2006-01-02T15:04:05.999999999", e.Deadline, loc)
		if err != nil {
			return errors.New("invalid start deadline")
		}
		if e.Scheduled != "" {
			scheduled, err := time.ParseInLocation("2006-01-02T15:04:05.999999999", e.Scheduled, loc)
			if err != nil {
				return err
			}
			if envelope.ServerTime.Before(scheduled) {
				continue
			}
		}
		j := &journal{AgentID: envelope.AgentID, Generation: envelope.Generation, Execution: e, Phase: phaseStarting}

		// HTTP開始通知・OSプロセス作成より先に開始意図を永続化する。
		if err = d.save(j); err != nil {
			return err
		}
		d.journals[id] = j
		if e.Cancelled != "" || !envelope.ServerTime.Before(deadline) {
			status := executionExpired
			if e.Cancelled != "" {
				status = executionCancelled
			}
			if err = d.finished(completion{id, result{Status: status, NotStarted: true}}); err != nil {
				return err
			}
			continue
		}
		var accepted execution

		// 開始受理はBackendの時刻で判定し、端末時計と一致することを前提にしない。
		if err = d.api("POST", "/api/v1/agent/batch/executions/"+id+"/start", nil, &accepted); err != nil {
			j.Phase = phaseUnknown
			if saveErr := d.save(j); saveErr != nil {
				return saveErr
			}
			continue
		}
		if accepted.ID != id {
			return errors.New("start response identity mismatch")
		}

		// 保守停止ではBackendが未開始状態を明示して返す。通信失敗とは区別し、次の照合で開始し直せるようにする。
		if accepted.Status == "queued" || accepted.Status == "dispatched" {
			if err = os.Remove(filepath.Join(d.jobsDir, id+".json")); err != nil {
				return err
			}
			if err = syncDirectory(d.jobsDir); err != nil {
				return err
			}
			delete(d.journals, id)
			continue
		}
		if terminal(accepted.Status) || accepted.Cancelled != "" {
			j.Phase = phaseUnknown
			if err = d.save(j); err != nil {
				return err
			}
			continue
		}
		if accepted.Status != executionRunning {
			return errors.New("start not accepted")
		}
		if ctx.Err() != nil {
			j.Phase = phaseUnknown
			return d.save(j)
		}
		j.Phase = phaseRunning
		if err = d.save(j); err != nil {
			return err
		}
		a := &activeProcess{cancel: make(chan struct{})}
		d.active[id] = a
		fmt.Fprintf(d.output, "実行開始: %s\n", id)

		// 実行goroutineには起動時点のトークン値を渡し、常駐ループが更新する共有stateを読み続けさせない。
		token := d.state.Tokens.AccessToken
		// 完了の回収・状態更新は常駐ループが担い、このgoroutineは通知後に終了する。
		go func() {
			d.done <- completion{id, executeWithAttachments(ctx, d.dir, d.client, token, e, d.config, a.cancel)}
		}()
	}
	return nil
}

// publishStatus は監視対象を取得し、ctxに従う実測収集に成功した場合だけ送信する。
// 対象取得・収集・送信の失敗は呼び出し元へ返し、次周期の再試行に委ねる。
func (d *daemon) publishStatus(ctx context.Context) error {
	var targets statusTargets
	if err := d.api("GET", "/api/v1/agent/status/targets", nil, &targets); err != nil {
		return err
	}
	snapshot, err := d.statusCollector(ctx, targets.Services)
	if err != nil {
		return err
	}
	return d.api("POST", "/api/v1/agent/status/snapshot", snapshot, nil)
}

// finished は完了通知を反映し、結果・保持期限を保存して実行中一覧から取り除く。
// unknownは確定結果を破棄し、作業領域の自動回収対象にも新たに加えない。保存失敗はエラーにする。
func (d *daemon) finished(c completion) error {
	delete(d.active, c.id)
	j := d.journals[c.id]
	j.Result = &c.value
	j.CompletedAt = time.Now()
	if c.value.Status != executionUnknown && j.Execution.WorkRetentionDays > 0 {
		j.WorkStopped = true
		j.CleanupAfter = j.CompletedAt.Add(time.Duration(j.Execution.WorkRetentionDays) * 24 * time.Hour)
	}
	j.Phase = phaseCompleted
	if c.value.Status == executionUnknown {
		j.Phase = phaseUnknown
		j.Result = nil
	}
	if err := d.save(j); err != nil {
		return err
	}
	fmt.Fprintf(d.output, "実行終了: %s (%s)\n", c.id, c.value.Status)
	return nil
}

// hasPendingResults は別登録世代も含む未受理結果の存在を返す。
// 保存容量の無制限な増加を避けるため、新規開始だけを止め、実行中の監視・取消・結果送信は継続する。
func (d *daemon) hasPendingResults() bool {
	for _, j := range d.journals {
		if j.Result != nil {
			return true
		}
	}
	return false
}

// deliver は完了結果を受理確認まで再送し、結果不明は専用APIで報告する。
// 送信試行回数を通信前に永続化し、送信確定後に結果を破棄する。コマンド自体は再実行しない。
// 完了送信の失敗は次回再試行へ回し、それ以外の照合・保存エラーは呼び出し元へ返す。
func (d *daemon) deliver(j *journal) error {
	base := "/api/v1/agent/batch/executions/" + j.Execution.ID
	if j.Phase == phaseUnknown {
		return d.api("POST", base+"/unconfirmed", map[string]string{"reason": "Agent再起動または開始・停止の確認不能。自動再実行は行いません。"}, nil)
	}
	if j.Phase != phaseCompleted || j.Result == nil {
		return nil
	}
	if time.Now().Before(j.NextAttempt) {
		return nil
	}
	if j.Execution.RetryInterval < 1 {
		return errors.New("invalid result retry interval; result retained")
	}
	if j.Attempts < maxResultAttempts {
		j.Attempts++
	}
	j.NextAttempt = time.Now().Add(time.Duration(j.Execution.RetryInterval) * time.Second)
	if err := d.save(j); err != nil {
		return err
	}

	// 同一実行IDの結果を再送するだけであり、タイムアウト・認証失敗でも本文を破棄しない。
	var accepted execution
	if err := d.api("POST", base+"/complete", j.Result, &accepted); err != nil {
		return nil
	}
	if accepted.ID != j.Execution.ID || accepted.Status != j.Result.Status || !terminal(accepted.Status) {
		return nil
	}

	// 受理状態の保存が失敗した場合はメモリにも結果を残し、次周期に同じIDで再照合する。
	acknowledged := *j
	acknowledged.Phase = phaseAcknowledged
	acknowledged.Result = nil
	if err := d.save(&acknowledged); err != nil {
		return err
	}
	*j = acknowledged
	return nil
}
