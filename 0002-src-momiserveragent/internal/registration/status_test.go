package registration

import (
	"context"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"
)

func TestPublishStatusUsesAssignedTargetsAndAgentAuthentication(t *testing.T) {
	var received statusSnapshot
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Header.Get("Authorization") != "Bearer token" {
			w.WriteHeader(http.StatusUnauthorized)
			return
		}
		switch r.URL.Path {
		case "/api/v1/agent/status/targets":
			io.WriteString(w, `{"services":[{"id":7,"service_name":"example-service"}]}`)
		case "/api/v1/agent/status/snapshot":
			if err := json.NewDecoder(r.Body).Decode(&received); err != nil {
				t.Fatal(err)
			}
			w.WriteHeader(http.StatusNoContent)
		default:
			w.WriteHeader(http.StatusNotFound)
		}
	}))
	defer server.Close()

	collectedAt := time.Now().UTC()
	d := &daemon{
		client: NewClient(server.URL),
		state:  &State{Tokens: &Tokens{AccessToken: "token", AccessExpiresAt: time.Now().Add(time.Hour)}},
		statusCollector: func(_ context.Context, targets []serviceTarget) (statusSnapshot, error) {
			if len(targets) != 1 || targets[0].ID != 7 || targets[0].Name != "example-service" {
				t.Fatalf("unexpected targets: %#v", targets)
			}
			return statusSnapshot{CollectedAt: collectedAt, CPUPercent: 12.5,
				MemoryPercent: 34.5, Storage: []storageSnapshot{}, Services: []serviceSnapshot{}}, nil
		},
	}
	if err := d.publishStatus(context.Background()); err != nil {
		t.Fatal(err)
	}
	if !received.CollectedAt.Equal(collectedAt) || received.CPUPercent != 12.5 || received.MemoryPercent != 34.5 {
		t.Fatalf("unexpected snapshot: %#v", received)
	}
}

func TestClampPercentRejectsInvalidAndOutOfRangeValues(t *testing.T) {
	for input, expected := range map[float64]float64{-5: 0, 25.126: 25.13, 120: 100} {
		if actual := clampPercent(input); actual != expected {
			t.Fatalf("clampPercent(%v) = %v, want %v", input, actual, expected)
		}
	}
}

func TestProcessPercentUsesCpuTimeDelta(t *testing.T) {
	if actual := processPercent(0.125, 250*time.Millisecond); actual != 50 {
		t.Fatalf("processPercent() = %v, want 50", actual)
	}
}
