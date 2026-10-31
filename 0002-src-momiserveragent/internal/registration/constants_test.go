package registration

import (
	"encoding/json"
	"testing"
	"time"
)

func TestProtocolConstantsPreserveStoredCodes(t *testing.T) {
	values := []struct {
		name, code, expected string
	}{
		{"version", version, "0.1.0"},
		{"executionRunning", executionRunning, "running"},
		{"executionSuccess", executionSuccess, "success"},
		{"executionFailed", executionFailed, "failed"},
		{"executionTimeout", executionTimeout, "timeout"},
		{"executionCancelled", executionCancelled, "cancelled"},
		{"executionExpired", executionExpired, "expired"},
		{"executionUnknown", executionUnknown, "unknown"},
		{"phaseStarting", phaseStarting, "starting"},
		{"phaseRunning", phaseRunning, "running"},
		{"phaseCompleted", phaseCompleted, "completed"},
		{"phaseAcknowledged", phaseAcknowledged, "acknowledged"},
		{"phaseUnknown", phaseUnknown, "unknown"},
		{"runtimeOnline", runtimeOnline, "online"},
		{"runtimeOffline", runtimeOffline, "offline"},
		{"runtimeUnknown", runtimeUnknown, "unknown"},
	}
	for _, value := range values {
		t.Run(value.name, func(t *testing.T) {
			encoded, err := json.Marshal(value.code)
			if err != nil || string(encoded) != `"`+value.expected+`"` {
				t.Fatalf("wire code changed: %s, %v", encoded, err)
			}
		})
	}
}

func TestSharedAuthenticationTimeoutsRemainUnchanged(t *testing.T) {
	if NewClient("https://example.com").HTTP.Timeout != 30*time.Second {
		t.Fatal("HTTP request timeout changed")
	}
	if accessRefreshLead != 5*time.Minute {
		t.Fatal("access token refresh lead changed")
	}
}
