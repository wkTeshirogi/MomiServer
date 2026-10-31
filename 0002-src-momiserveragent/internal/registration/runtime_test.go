package registration

import (
	"context"
	"encoding/json"
	mqtt "github.com/eclipse/paho.mqtt.golang"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"sync"
	"testing"
	"time"
)

func TestManagedProcessExitCaptureAndStop(t *testing.T) {
	command := "echo hello; echo problem >&2; exit 7"
	if runtime.GOOS == "windows" {
		command = "echo hello & echo problem 1>&2 & exit /b 7"
	}
	r := executeProcess(context.Background(), execution{Command: command, Timeout: 10, Grace: 1}, runtimeConfig{}, make(chan struct{}))
	if r.Status != "failed" || r.ExitCode != 7 || !strings.Contains(r.Stdout, "hello") || !strings.Contains(r.Stderr, "problem") || !r.StopConfirmed {
		t.Fatalf("unexpected result: %+v", r)
	}
	command = "sleep 30"
	if runtime.GOOS == "windows" {
		command = "ping -n 30 127.0.0.1 >nul"
	}
	for _, cancelled := range []bool{false, true} {
		cancel := make(chan struct{})
		if cancelled {
			close(cancel)
		}
		started := time.Now()
		r = executeProcess(context.Background(), execution{Command: command, Timeout: 1, Grace: 1}, runtimeConfig{}, cancel)
		want := "timeout"
		if cancelled {
			want = "cancelled"
		}
		if r.Status != want || !r.StopConfirmed || time.Since(started) > 10*time.Second {
			t.Fatalf("stop failed: %+v", r)
		}
	}
}

func TestOutputLimitAndRestartTombstone(t *testing.T) {
	var b boundedOutput
	payload := strings.Repeat("x", 100000)
	n, err := b.Write([]byte(payload))
	if err != nil || n != len(payload) || b.Len() != 65536 || !b.truncated {
		t.Fatal("capture is not bounded/draining")
	}
	dir := t.TempDir()
	id, _ := newID()
	j := journal{AgentID: "agent", Generation: 1, Execution: execution{ID: id}, Phase: "running"}
	if err := atomicJSON(dir, id+".json", j); err != nil {
		t.Fatal(err)
	}
	entries, err := loadJournals(dir)
	if err != nil {
		t.Fatal(err)
	}
	if entries[id].Phase != "unknown" {
		t.Fatal("restart may rerun active execution")
	}
	if err := os.WriteFile(filepath.Join(dir, id+".json"), []byte("broken"), 0600); err != nil {
		t.Fatal(err)
	}
	if _, err := loadJournals(dir); err == nil {
		t.Fatal("corrupt journal ignored")
	}
}

func TestResultRetriesPastLegacyLimitsUntilMatchingAcknowledgement(t *testing.T) {
	posts := 0
	unknown := 0
	status := http.StatusServiceUnavailable
	id, _ := newID()
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if strings.HasSuffix(r.URL.Path, "/complete") {
			posts++
			w.WriteHeader(status)
			if status == http.StatusOK {
				json.NewEncoder(w).Encode(execution{ID: id, Status: executionSuccess})
			}
		} else {
			unknown++
			w.WriteHeader(200)
		}
	}))
	defer server.Close()
	dir := t.TempDir()
	j := &journal{AgentID: "agent", Generation: 1, Execution: execution{ID: id, RetryCount: 1, RetryInterval: 1, RetentionDays: 7}, Phase: "completed", CompletedAt: time.Now().Add(-365 * 24 * time.Hour), Result: &result{Status: "success", Stdout: "retained"}}
	d := &daemon{dir: dir, jobsDir: dir, client: NewClient(server.URL), state: &State{Tokens: &Tokens{AccessExpiresAt: time.Now().Add(time.Hour), AccessToken: "ma_test"}}}
	if err := d.deliver(j); err != nil {
		t.Fatal(err)
	}
	loaded, err := loadJournals(dir)
	if err != nil {
		t.Fatal(err)
	}
	j = loaded[id]
	if j.Attempts != 1 {
		t.Fatal("attempt not persisted")
	}
	j.NextAttempt = time.Time{}
	if err := d.deliver(j); err != nil {
		t.Fatal(err)
	}
	j.NextAttempt = time.Time{}
	if err := d.deliver(j); err != nil {
		t.Fatal(err)
	}
	if posts != 3 || unknown != 0 || j.Result == nil {
		t.Fatalf("legacy limit stopped delivery or deleted result: %d %d", posts, unknown)
	}
	if err := d.deliver(j); err != nil || posts != 3 {
		t.Fatal("retry interval was ignored", err)
	}
	status = http.StatusUnauthorized
	j.NextAttempt = time.Time{}
	if err := d.deliver(j); err != nil || j.Result == nil {
		t.Fatal("authentication failure discarded result", err)
	}
	status = http.StatusOK
	j.NextAttempt = time.Time{}
	if err := d.deliver(j); err != nil {
		t.Fatal(err)
	}
	loaded, err = loadJournals(dir)
	if err != nil {
		t.Fatal(err)
	}
	if loaded[id].Result != nil || loaded[id].Phase != phaseAcknowledged || loaded[id].Attempts != 5 {
		t.Fatal("matching acknowledgement did not persist tombstone")
	}
	if err := d.deliver(loaded[id]); err != nil || posts != 5 {
		t.Fatal("acknowledged result was sent again", err)
	}
}

func TestRunningServerRecordWithoutLocalJournalIsNeverExecuted(t *testing.T) {
	id, _ := newID()
	starts := 0
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch r.URL.Path {
		case "/api/v1/agent/batch/settings":
			io.WriteString(w, `{"max_concurrent_executions":3}`)
		case "/api/v1/agent/runtime/environment":
			io.WriteString(w, `{"shell":"/bin/sh","shell_args":["-c"]}`)
		case "/api/v1/agent/mqtt/settings":
			io.WriteString(w, `{"enabled":false,"status":"pending"}`)
		case "/api/v1/agent/heartbeat":
			io.WriteString(w, `{}`)
		case "/api/v1/agent/runtime/sync":
			json.NewEncoder(w).Encode(map[string]any{"execution_ids": []string{id}})
		case "/api/v1/agent/batch/executions/" + id + "/command":
			json.NewEncoder(w).Encode(map[string]any{"agent_id": "agent", "generation": 1, "execution": execution{ID: id, Status: "running", Command: "must never execute"}})
		default:
			starts++
			w.WriteHeader(500)
		}
	}))
	defer server.Close()
	dir := t.TempDir()
	d := &daemon{dir: dir, jobsDir: dir, client: NewClient(server.URL), state: &State{Tokens: &Tokens{AgentID: "agent", Generation: 1, AccessExpiresAt: time.Now().Add(time.Hour)}},
		journals: map[string]*journal{}, active: map[string]*activeProcess{}, broker: newRuntimeBroker(true), output: io.Discard}
	if err := d.cycle(context.Background()); err != nil {
		t.Fatal(err)
	}
	if starts != 0 || len(d.active) != 0 || d.journals[id].Phase != "unknown" {
		t.Fatal("ambiguous server execution launched")
	}
}

func TestRuntimeConcurrencyCancellationAndDuplicateSynchronization(t *testing.T) {
	var lock sync.Mutex
	jobs := map[string]execution{}
	ids := []string{}
	starts := map[string]int{}
	for i := 0; i < 4; i++ {
		id, _ := newID()
		ids = append(ids, id)
		command := "sleep 2; echo done"
		if runtime.GOOS == "windows" {
			command = "ping -n 3 127.0.0.1 >nul & echo done"
		}
		jobs[id] = execution{ID: id, Status: "queued", Command: command, Timeout: 10, Grace: 1, RetryCount: 1, RetryInterval: 1, RetentionDays: 7, Deadline: time.Now().UTC().Add(time.Hour).Format("2006-01-02T15:04:05")}
	}
	connection := mqttConnection{Enabled: true, Status: "ready", Broker: "tcp://127.0.0.1:1", ClientID: "fixture", Username: "fixture", Password: "fixture", CommandTopic: "fixture/command", CancelTopic: "fixture/cancel"}
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		lock.Lock()
		defer lock.Unlock()
		switch r.URL.Path {
		case "/api/v1/agent/batch/settings":
			json.NewEncoder(w).Encode(settings{Max: 3})
			return
		case "/api/v1/agent/mqtt/settings":
			json.NewEncoder(w).Encode(connection)
			return
		case "/api/v1/agent/heartbeat":
			io.WriteString(w, `{}`)
			return
		case "/api/v1/agent/runtime/sync":
			json.NewEncoder(w).Encode(map[string]any{"execution_ids": ids})
			return
		}
		for id, e := range jobs {
			base := "/api/v1/agent/batch/executions/" + id
			switch r.URL.Path {
			case base + "/command":
				json.NewEncoder(w).Encode(map[string]any{"agent_id": "agent", "generation": 1, "server_time": time.Now().UTC(), "time_zone": "UTC", "execution": e})
				return
			case base + "/start":
				starts[id]++
				e.Status = "running"
				jobs[id] = e
				json.NewEncoder(w).Encode(e)
				return
			case base + "/complete":
				var value result
				json.NewDecoder(r.Body).Decode(&value)
				e.Status = value.Status
				jobs[id] = e
				json.NewEncoder(w).Encode(e)
				return
			}
		}
		w.WriteHeader(404)
	}))
	defer server.Close()
	dir := t.TempDir()
	shell, args := defaultShell()
	d := &daemon{dir: dir, jobsDir: dir, client: NewClient(server.URL), state: &State{Tokens: &Tokens{AgentID: "agent", Generation: 1, AccessExpiresAt: time.Now().Add(time.Hour)}},
		journals: map[string]*journal{}, active: map[string]*activeProcess{}, done: make(chan completion, 10), broker: newRuntimeBroker(true), config: runtimeConfig{Shell: shell, ShellArgs: args}, output: io.Discard}
	// This unit test substitutes a previously verified broker setup; real MQTT is covered separately.
	d.broker.settings = connection
	ctx, cancel := context.WithTimeout(context.Background(), 20*time.Second)
	defer cancel()
	if err := d.cycle(ctx); err != nil {
		t.Fatal(err)
	}
	if len(d.active) != 3 {
		t.Fatalf("concurrency: %d", len(d.active))
	}
	if err := d.cycle(ctx); err != nil {
		t.Fatal(err)
	}
	lock.Lock()
	for _, n := range starts {
		if n != 1 {
			t.Error("duplicate start")
		}
	}
	e := jobs[ids[0]]
	e.Cancelled = time.Now().Format("2006-01-02T15:04:05")
	jobs[ids[0]] = e
	lock.Unlock()
	if err := d.cycle(ctx); err != nil {
		t.Fatal(err)
	}
	completed := 0
	for completed < 4 {
		select {
		case c := <-d.done:
			if err := d.finished(c); err != nil {
				t.Fatal(err)
			}
			completed++
		case <-ctx.Done():
			t.Fatal("runtime stalled")
		}
		if err := d.cycle(ctx); err != nil {
			t.Fatal(err)
		}
	}
	lock.Lock()
	defer lock.Unlock()
	if jobs[ids[0]].Status != "cancelled" {
		t.Fatal("cancellation not delivered")
	}
	for _, id := range ids {
		if starts[id] != 1 {
			t.Fatal("execution count mismatch")
		}
	}
}

// Full Go -> HTTP backend -> Mosquitto -> OS process -> HTTP result verification.
func TestLiveRuntime(t *testing.T) {
	server, code, id := os.Getenv("AGENT_TEST_SERVER"), os.Getenv("AGENT_TEST_CODE"), os.Getenv("AGENT_TEST_EXECUTION")
	definition := os.Getenv("AGENT_TEST_DEFINITION")
	if server == "" || code == "" || (id == "" && definition == "") {
		t.Skip("isolated runtime environment not configured")
	}
	dir := t.TempDir()
	jobs := filepath.Join(dir, "executions")
	if err := prepareDirectory(jobs); err != nil {
		t.Fatal(err)
	}
	state, err := NewState(server, code)
	if err != nil {
		t.Fatal(err)
	}
	client := NewClient(server)
	state.Tokens, err = client.Register(state, code, "runtime-e2e", runtime.GOOS)
	if err != nil {
		t.Fatal(err)
	}
	if definition != "" {
		var login struct {
			AccessToken string `json:"accessToken"`
		}
		err = client.request("POST", "/api/auth/login", "", map[string]string{"email": os.Getenv("AGENT_TEST_ADMIN_EMAIL"), "password": os.Getenv("AGENT_TEST_ADMIN_PASSWORD")}, &login)
		if err != nil {
			t.Fatal(err)
		}
		var created execution
		err = client.request("POST", "/api/batch/executions/definitions/"+definition, login.AccessToken, map[string]any{"target_agent_id": state.Tokens.DeviceID, "test_execution": true}, &created)
		if err != nil {
			t.Fatal(err)
		}
		id = created.ID
		if len(created.Attachments) == 0 || created.WorkRetentionDays < 1 {
			t.Fatal("attachment snapshot missing")
		}
	}

	if err = saveState(dir, state); err != nil {
		t.Fatal(err)
	}
	d := &daemon{dir: dir, jobsDir: jobs, state: state, client: client, journals: map[string]*journal{}, active: map[string]*activeProcess{},
		done: make(chan completion, 10), broker: newRuntimeBroker(true), output: io.Discard}
	defer d.broker.close()
	ctx, cancel := context.WithTimeout(context.Background(), 90*time.Second)
	defer cancel()
	for ctx.Err() == nil {
		if err = d.cycle(ctx); err != nil {
			t.Fatal(err)
		}
		select {
		case c := <-d.done:
			if err = d.finished(c); err != nil {
				t.Fatal(err)
			}
		case <-time.After(time.Second):
		}
		if j := d.journals[id]; j != nil && j.Phase == "acknowledged" {
			var raw map[string]any
			if err = d.api("GET", "/api/v1/agent/batch/executions/"+id+"/command", nil, &raw); err != nil {
				t.Fatal(err)
			}
			value := raw["execution"].(map[string]any)
			if value["execution_status"] != "success" || !strings.Contains(value["stdout"].(string), "runtime-e2e") {
				t.Fatalf("result not accepted: %v", value["execution_status"])
			}
			if !d.broker.connected() {
				t.Fatal("MQTT not connected")
			}
			options := mqtt.NewClientOptions().AddBroker(d.broker.settings.Broker).SetClientID("runtime-test-publisher").SetUsername("runtime-publisher").SetPassword("runtime-publish-only")
			publisher := mqtt.NewClient(options)
			connected := publisher.Connect()
			if !connected.WaitTimeout(5*time.Second) || connected.Error() != nil {
				t.Fatal("test publisher connection failed")
			}
			defer publisher.Disconnect(100)
			published := publisher.Publish(d.broker.settings.CommandTopic, 1, false, `{"execution_id":"`+id+`"}`)
			if !published.WaitTimeout(5*time.Second) || published.Error() != nil {
				t.Fatal("notification publish failed")
			}
			limit := time.Now().Add(5 * time.Second)
			for d.broker.messages.Load() == 0 && time.Now().Before(limit) {
				time.Sleep(20 * time.Millisecond)
			}
			if d.broker.messages.Load() == 0 {
				t.Fatal("MQTT notification not received")
			}
			if err = d.cycle(ctx); err != nil {
				t.Fatal(err)
			}
			if len(d.active) != 0 {
				t.Fatal("completed execution rerun")
			}
			return
		}
	}
	t.Fatal("runtime execution did not complete")
}
