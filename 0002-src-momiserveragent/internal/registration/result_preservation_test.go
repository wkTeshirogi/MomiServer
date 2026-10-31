package registration

import (
	"context"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"
	"time"
)

func TestUnrelatedOrMalformedAcknowledgementRetainsResult(t *testing.T) {
	for _, body := range []string{`{}`, `broken`, `{"execution_id":"other","execution_status":"success"}`, `{"execution_status":"failed"}`} {
		t.Run(body, func(t *testing.T) {
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { io.WriteString(w, body) }))
			defer server.Close()
			d, j := resultFixture(t, server.URL)
			j.Attempts = maxResultAttempts
			if err := d.deliver(j); err != nil {
				t.Fatal(err)
			}
			loaded, err := loadJournals(d.jobsDir)
			if err != nil || loaded[j.Execution.ID].Result == nil || j.Attempts != maxResultAttempts {
				t.Fatal("unconfirmed result lost or counter overflowed", err)
			}
		})
	}
}

func TestResultSavedBeforeNetworkAndAcknowledgementSaveFailurePreservesMemory(t *testing.T) {
	var d *daemon
	var j *journal
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		loaded, err := loadJournals(d.jobsDir)
		if err != nil || loaded[j.Execution.ID].Attempts != 1 || loaded[j.Execution.ID].Result == nil {
			t.Error("network preceded durable attempt", err)
		}
		// 保存先を壊すのはこの試験専用ディレクトリだけ。通信後の永続化失敗を再現する。
		d.jobsDir = filepath.Join(d.dir, "missing")
		json.NewEncoder(w).Encode(execution{ID: j.Execution.ID, Status: executionSuccess})
	}))
	defer server.Close()
	d, j = resultFixture(t, server.URL)
	if err := d.deliver(j); err == nil || j.Result == nil || j.Phase != phaseCompleted {
		t.Fatal("save failure discarded in-memory result", err)
	}
}

func TestUnwritableJournalStopsBeforeSending(t *testing.T) {
	posts := 0
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { posts++ }))
	defer server.Close()
	d, j := resultFixture(t, server.URL)
	d.jobsDir = filepath.Join(d.dir, "missing")
	if err := d.deliver(j); err == nil || posts != 0 || j.Result == nil {
		t.Fatal("write failure sent or discarded result", err)
	}
}

func TestResultDeliveryPrecedesSettingsFailure(t *testing.T) {
	posts := 0
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path == "/api/v1/agent/batch/settings" {
			w.WriteHeader(http.StatusServiceUnavailable)
			return
		}
		posts++
		w.WriteHeader(http.StatusServiceUnavailable)
	}))
	defer server.Close()
	d, j := resultFixture(t, server.URL)
	d.journals = map[string]*journal{j.Execution.ID: j}
	if err := d.cycle(context.Background()); err == nil || posts != 1 || !d.hasPendingResults() {
		t.Fatal("settings failure starved result or backpressure absent", err)
	}
}

func TestPendingResultsAndDeferredStartsNeverLaunchProcesses(t *testing.T) {
	id, _ := newID()
	connection := mqttConnection{Enabled: true, Status: "ready", Broker: "tcp://127.0.0.1:1", ClientID: "fixture", Username: "fixture", Password: "fixture", CommandTopic: "fixture/command", CancelTopic: "fixture/cancel"}
	e := execution{ID: id, Status: "dispatched", Command: "must not execute", Timeout: 10, RetryInterval: 1, Deadline: time.Now().UTC().Add(time.Hour).Format("2006-01-02T15:04:05")}
	starts := 0
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch r.URL.Path {
		case "/api/v1/agent/mqtt/settings":
			json.NewEncoder(w).Encode(connection)
		case "/api/v1/agent/runtime/sync":
			json.NewEncoder(w).Encode(map[string]any{"execution_ids": []string{id}})
		case "/api/v1/agent/batch/executions/" + id + "/command":
			json.NewEncoder(w).Encode(map[string]any{"agent_id": "agent", "generation": 1, "server_time": time.Now().UTC(), "time_zone": "UTC", "execution": e})
		case "/api/v1/agent/batch/executions/" + id + "/start":
			starts++
			json.NewEncoder(w).Encode(e)
		default:
			io.WriteString(w, `{}`)
		}
	}))
	defer server.Close()
	d, _ := resultFixture(t, server.URL)
	d.journals = map[string]*journal{}
	d.active = map[string]*activeProcess{}
	d.timeSettings = time.Now()
	d.settings.Max = 1
	d.broker = newRuntimeBroker(true)
	d.broker.settings = connection
	d.output = io.Discard
	_, pending := resultFixture(t, server.URL)
	d.journals[pending.Execution.ID] = pending
	if err := d.cycle(context.Background()); err != nil || starts != 0 || len(d.active) != 0 || pending.Result == nil {
		t.Fatal("pending result did not prevent new execution", err)
	}
	// 未受理結果がなくなった後も、保守による開始保留はプロセス起動・不明記録を発生させない。
	delete(d.journals, pending.Execution.ID)
	for i := 0; i < 2; i++ {
		if err := d.cycle(context.Background()); err != nil {
			t.Fatal(err)
		}
		if len(d.journals) != 0 || len(d.active) != 0 {
			t.Fatal("maintenance denial executed or left unknown tombstone")
		}
		if _, err := os.Stat(filepath.Join(d.jobsDir, id+".json")); !os.IsNotExist(err) {
			t.Fatal("deferred intent not cleared", err)
		}
	}
	if starts != 2 {
		t.Fatal("deferred start could not retry")
	}
}

func resultFixture(t *testing.T, server string) (*daemon, *journal) {
	t.Helper()
	dir := t.TempDir()
	id, _ := newID()
	j := &journal{AgentID: "agent", Generation: 1, Execution: execution{ID: id, RetryInterval: 1}, Phase: phaseCompleted,
		CompletedAt: time.Now().Add(-365 * 24 * time.Hour), Result: &result{Status: executionSuccess, Stdout: "retained"}}
	d := &daemon{dir: dir, jobsDir: dir, client: NewClient(server), state: &State{Tokens: &Tokens{
		AgentID: "agent", Generation: 1, AccessExpiresAt: time.Now().Add(time.Hour), AccessToken: "ma_test"}}}
	return d, j
}
