package registration

import (
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

func TestLocalHTTPURLBoundary(t *testing.T) {
	for _, raw := range []string{"http://localhost:8080", "http://LOCALHOST:8080/base", "http://127.0.0.1:8080", "http://[::1]:8080"} {
		if _, err := serverURLWithOptions(raw, true); err != nil {
			t.Errorf("local URL rejected: %s: %v", raw, err)
		}
		if _, err := serverURL(raw); err == nil {
			t.Errorf("HTTP accepted without flag: %s", raw)
		}
	}
	for _, raw := range []string{
		"http://example.com", "http://192.168.1.1", "http://0.0.0.0", "http://127.1", "http://2130706433",
		"http://localhost.example.com", "http://localhost.", "http://[::ffff:127.0.0.1]", "http://[::1%25eth0]",
		"http://localhost@evil.invalid", "http://user:secret@localhost", "http://localhost?code=x", "http://localhost?",
		"http://localhost#fragment", "http:localhost:8080", "[http://localhost:8080](http://localhost:8080)", "ftp://localhost",
		"http://[localhost]:8080", "http://[127.0.0.1]:8080", "https://[localhost]:443", "https://[127.0.0.1]:443",
	} {
		if _, err := serverURLWithOptions(raw, true); err == nil {
			t.Errorf("unsafe URL accepted: %s", raw)
		}
	}
	if _, err := serverURLWithOptions("https://example.com", true); err != nil {
		t.Fatal(err)
	}
}

func TestLocalHTTPCommandLifecycleRequiresFlagEachTime(t *testing.T) {
	requests := 0
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		requests++
		switch r.URL.Path {
		case "/api/v1/agent/auth/challenge":
			json.NewEncoder(w).Encode(map[string]string{"nonce": "local-test"})
		case "/api/v1/agent/register", "/api/v1/agent/auth/refresh":
			json.NewEncoder(w).Encode(Tokens{AgentID: "test-agent", DeviceID: 1, Generation: 1,
				TokenType: "Bearer", AccessToken: "ma_test", RefreshToken: "mr_test",
				AccessExpiresAt: time.Now().Add(time.Hour), RefreshExpiresAt: time.Now().Add(24 * time.Hour)})
		case "/api/v1/agent/me":
			if r.Header.Get("Authorization") != "Bearer ma_test" {
				t.Error("missing credentials")
			}
			json.NewEncoder(w).Encode(map[string]string{"status": "registered"})
		default:
			t.Error("unexpected request")
			w.WriteHeader(404)
		}
	}))
	defer server.Close()
	dir := filepath.Join(t.TempDir(), "agent")
	// Also exercise localhost's direct loopback dial, independently of DNS and proxies.
	base := strings.Replace(server.URL, "127.0.0.1", "localhost", 1)
	register := []string{"register", "test-code", "--server", base, "--state-dir", dir}
	if err := Run(register, os.Stdin, io.Discard); err == nil || requests != 0 {
		t.Fatal("registration without flag reached HTTP")
	}
	if err := Run(append(register, "--allow-insecure-localhost"), os.Stdin, io.Discard); err != nil {
		t.Fatal(err)
	}
	for _, command := range []string{"status", "refresh"} {
		before := requests
		args := []string{command, "--state-dir", dir}
		if err := Run(args, os.Stdin, io.Discard); err == nil || requests != before {
			t.Fatalf("%s reused HTTP permission from saved state", command)
		}
		if err := Run(append(args, "--allow-insecure-localhost"), os.Stdin, io.Discard); err != nil {
			t.Fatal(err)
		}
	}
}

func TestLocalHTTPDoesNotRedirectOrUseProxy(t *testing.T) {
	received := false
	destination := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { received = true }))
	defer destination.Close()
	origin := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		http.Redirect(w, r, destination.URL, http.StatusTemporaryRedirect)
	}))
	defer origin.Close()
	client := NewClient(origin.URL)
	transport := client.HTTP.Transport.(*http.Transport)
	if transport.Proxy != nil {
		t.Fatal("local HTTP could use environment proxy")
	}
	err := client.request("POST", "/register", "", map[string]string{"registrationCode": "test-secret"}, nil)
	if err == nil || received || strings.Contains(err.Error(), "test-secret") {
		t.Fatal("local HTTP redirect boundary failed")
	}
}
