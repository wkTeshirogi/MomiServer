package registration

import (
	"context"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"testing"
	"time"
)

func TestAttachmentPreparationAndExecution(t *testing.T) {
	script := "cat input/data.txt"
	name := "run.sh"
	command := "/bin/sh run.sh"
	if runtime.GOOS == "windows" {
		script = "@type input\\data.txt\r\n"
		name = "run.cmd"
		command = "cmd.exe /D /C run.cmd"
	}
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Header.Get("Authorization") != "Bearer fixture" {
			w.WriteHeader(401)
			return
		}
		if strings.HasSuffix(r.URL.Path, "/1") {
			io.WriteString(w, script)
		} else {
			io.WriteString(w, "attachment-test")
		}
	}))
	defer server.Close()
	e := execution{ID: "11111111-1111-4111-8111-111111111111", Command: command, Timeout: 10, Grace: 1, WorkRetentionDays: 7,
		Attachments: []attachment{{1, name, int64(len(script)), digest(script)}, {2, "input/data.txt", 15, digest("attachment-test")}}}
	dir := t.TempDir()
	result := executeWithAttachments(context.Background(), dir, NewClient(server.URL), "fixture", e, runtimeConfig{}, make(chan struct{}))
	if result.Status != "success" || !strings.Contains(result.Stdout, "attachment-test") {
		t.Fatalf("execution failed: %+v", result)
	}
	if _, err := prepareAttachments(context.Background(), dir, NewClient(server.URL), "fixture", e); err == nil {
		t.Fatal("reused old work directory")
	}
	e.ID = "22222222-2222-4222-8222-222222222222"
	e.Attachments[0].Hash = strings.Repeat("0", 64)
	result = executeWithAttachments(context.Background(), dir, NewClient(server.URL), "fixture", e, runtimeConfig{}, make(chan struct{}))
	if result.Status != "failed" || !result.NotStarted {
		t.Fatal("corrupt script was executed")
	}
}

func TestAttachmentPathsAndCancellation(t *testing.T) {
	for _, name := range []string{"../x", "/x", "C:/x", "input/../x", "NUL.txt", "a\\b", "x.", "x/"} {
		if validDestination(name) {
			t.Fatal(name)
		}
	}
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { <-r.Context().Done() }))
	defer server.Close()
	cancel := make(chan struct{})
	go func() { time.Sleep(50 * time.Millisecond); close(cancel) }()
	e := execution{ID: "11111111-1111-4111-8111-111111111111", Timeout: 10, WorkRetentionDays: 7, Attachments: []attachment{{1, "input.txt", 1, digest("x")}}}
	result := executeWithAttachments(context.Background(), t.TempDir(), NewClient(server.URL), "fixture", e, runtimeConfig{}, cancel)
	if result.Status != "cancelled" || !result.StopConfirmed {
		t.Fatal("download not cancelled")
	}
}

func TestWorkCleanupProtectsUnknownJobsResultsAndExternalFiles(t *testing.T) {
	dir := t.TempDir()
	jobs := filepath.Join(dir, "executions")
	if err := prepareDirectory(jobs); err != nil {
		t.Fatal(err)
	}
	d := &daemon{dir: dir, jobsDir: jobs, journals: map[string]*journal{}, active: map[string]*activeProcess{}}
	root, err := workRoot(dir)
	if err != nil {
		t.Fatal(err)
	}
	defer root.Close()
	id := "11111111-1111-4111-8111-111111111111"
	if err = root.Mkdir(id, 0700); err != nil {
		t.Fatal(err)
	}
	outside := filepath.Join(t.TempDir(), "keep.txt")
	os.WriteFile(outside, []byte("keep"), 0600)
	// Windows runners may lack symlink privileges; Linux verifies this boundary too.
	_ = os.Symlink(outside, filepath.Join(dir, "work", id, "link"))
	j := &journal{Execution: execution{ID: id}, Phase: "unknown", CleanupAfter: time.Now().Add(-time.Hour), Result: &result{Status: "success", Stdout: "retained"}}
	d.journals[id] = j
	if err = d.cleanupWork(time.Now()); err != nil {
		t.Fatal(err)
	}
	if _, err = root.Stat(id); err != nil {
		t.Fatal("unknown work removed")
	}
	j.Phase = "unknown" // Result retention may expire before work retention; stop evidence is independent.
	j.WorkStopped = true
	if err = d.cleanupWork(time.Now()); err != nil {
		t.Fatal(err)
	}
	if _, err = root.Stat(id); !os.IsNotExist(err) {
		t.Fatal("expired work retained")
	}
	if _, err = os.Stat(outside); err != nil {
		t.Fatal("external file removed")
	}
	if j.Result == nil || j.Result.Stdout != "retained" {
		t.Fatal("retry result deleted")
	}
}
