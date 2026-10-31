package registration

import (
	"crypto/ed25519"
	"crypto/x509"
	"encoding/base64"
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

func TestSignedRegistrationAndRefresh(t *testing.T) {
	code := "test-registration-code"
	state, err := NewState("", code)
	if err != nil {
		t.Fatal(err)
	}
	var public ed25519.PublicKey
	received := 0
	server := httptest.NewTLSServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path == "/api/v1/agent/auth/challenge" {
			json.NewEncoder(w).Encode(map[string]string{"nonce": "test-nonce"})
			return
		}
		var body map[string]string
		if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
			t.Error(err)
			w.WriteHeader(400)
			return
		}
		purpose, id, credential := "register", state.RegistrationRequestID, code
		if r.URL.Path == "/api/v1/agent/register" {
			der, _ := base64.StdEncoding.DecodeString(body["publicKey"])
			parsed, err := x509.ParsePKIXPublicKey(der)
			if err != nil {
				t.Error(err)
				w.WriteHeader(400)
				return
			}
			public = parsed.(ed25519.PublicKey)
		} else if r.URL.Path == "/api/v1/agent/auth/refresh" {
			purpose, id, credential = "refresh", state.RefreshRequestID, state.Tokens.RefreshToken
		} else {
			t.Error("unexpected path")
			w.WriteHeader(404)
			return
		}
		parts := strings.Split(body["proof"], ".")
		if len(parts) != 3 {
			t.Error("not JWS")
			w.WriteHeader(400)
			return
		}
		signature, _ := base64.RawURLEncoding.DecodeString(parts[2])
		if !ed25519.Verify(public, []byte(parts[0]+"."+parts[1]), signature) {
			t.Error("invalid signature")
		}
		var claims map[string]string
		payload, _ := base64.RawURLEncoding.DecodeString(parts[1])
		json.Unmarshal(payload, &claims)
		if claims["aud"] != "momiserver-agent-"+purpose || claims["nonce"] != "test-nonce" ||
			claims["requestId"] != id || claims["credentialHash"] != digest(credential) {
			t.Error("unbound proof")
		}
		received++
		json.NewEncoder(w).Encode(Tokens{AgentID: "00000000-0000-4000-8000-000000000001", DeviceID: 1, Generation: 1,
			TokenType: "Bearer", AccessToken: "ma_test", RefreshToken: "mr_test",
			AccessExpiresAt: time.Now().Add(15 * time.Minute), RefreshExpiresAt: time.Now().Add(24 * time.Hour)})
	}))
	defer server.Close()
	client := &Client{Server: server.URL, HTTP: server.Client()}
	state.Tokens, err = client.Register(state, code, "fixture", "linux")
	if err != nil {
		t.Fatal(err)
	}
	state.RefreshRequestID, _ = newID()
	if _, err = client.Refresh(state); err != nil {
		t.Fatal(err)
	}
	if received != 2 {
		t.Fatal("registration and refresh not received")
	}
}

func TestStateSurvivesRestartAndRejectsConcurrentUse(t *testing.T) {
	dir := filepath.Join(t.TempDir(), "agent")
	if err := prepareDirectory(dir); err != nil {
		t.Fatal(err)
	}
	release, err := lockDirectory(dir)
	if err != nil {
		t.Fatal(err)
	}
	if second, err := lockDirectory(dir); err == nil {
		second()
		t.Error("concurrent operation allowed")
	}
	state, err := NewState("https://example.invalid", "code")
	if err != nil {
		t.Fatal(err)
	}
	if err = saveState(dir, state); err != nil {
		t.Fatal(err)
	}
	release()
	restored, err := loadState(dir)
	if err != nil {
		t.Fatal(err)
	}
	if restored.PrivateKey != state.PrivateKey || restored.RegistrationRequestID != state.RegistrationRequestID {
		t.Fatal("registration identity lost after restart")
	}
	restored.RefreshRequestID, _ = newID()
	if err = saveState(dir, restored); err != nil {
		t.Fatal(err)
	}
	again, err := loadState(dir)
	if err != nil || again.RefreshRequestID != restored.RefreshRequestID {
		t.Fatal("refresh intent not durable")
	}
	raw, _ := os.ReadFile(filepath.Join(dir, "credentials.json"))
	if strings.Contains(string(raw), `"registrationCode":`) {
		t.Fatal("plaintext code persisted")
	}
	os.WriteFile(filepath.Join(dir, "credentials.json"), []byte("broken"), 0600)
	if _, err = loadState(dir); err == nil {
		t.Fatal("corrupt state accepted")
	}
}

func TestHTTPSOnlyAndNoRedirectOrReflectedSecrets(t *testing.T) {
	for _, url := range []string{"http://example.com", "https://user:secret@example.com", "https://example.com?code=secret", "https://example.com#secret"} {
		if _, err := serverURL(url); err == nil {
			t.Errorf("unsafe URL accepted: %s", url)
		}
	}
	var received bool
	destination := httptest.NewTLSServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { received = true }))
	defer destination.Close()
	origin := httptest.NewTLSServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		http.Redirect(w, r, destination.URL, http.StatusTemporaryRedirect)
	}))
	defer origin.Close()
	client := NewClient(origin.URL)
	client.HTTP.Transport = origin.Client().Transport
	err := client.request("POST", "/api/v1/agent/register", "", map[string]string{"registrationCode": "do-not-leak"}, nil)
	if err == nil || received || strings.Contains(err.Error(), "do-not-leak") {
		t.Fatal("redirect/secret boundary failed")
	}
	reflector := httptest.NewTLSServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(500)
		io.Copy(w, r.Body)
	}))
	defer reflector.Close()
	client = &Client{Server: reflector.URL, HTTP: reflector.Client()}
	err = client.request("POST", "/register", "", map[string]string{"registrationCode": "do-not-leak"}, nil)
	if err == nil || strings.Contains(err.Error(), "do-not-leak") {
		t.Fatal("response exposed credential")
	}
}

func TestNonInteractiveInputAndServerRebindingAreRejected(t *testing.T) {
	input, err := os.CreateTemp(t.TempDir(), "stdin")
	if err != nil {
		t.Fatal(err)
	}
	defer input.Close()
	if err = Run([]string{"register"}, input, io.Discard); err == nil {
		t.Fatal("non-interactive code omission accepted")
	}
	dir := filepath.Join(t.TempDir(), "agent")
	if err = prepareDirectory(dir); err != nil {
		t.Fatal(err)
	}
	state, _ := NewState("https://original.invalid", "code")
	if err = saveState(dir, state); err != nil {
		t.Fatal(err)
	}
	err = Run([]string{"register", "code", "--state-dir", dir, "--server", "https://other.invalid"}, input, io.Discard)
	if err == nil {
		t.Fatal("credentials could be sent to another server")
	}
}

func TestInterruptedReregistrationRetainsIdentity(t *testing.T) {
	dir := filepath.Join(t.TempDir(), "agent")
	if err := prepareDirectory(dir); err != nil {
		t.Fatal(err)
	}
	state, err := NewState("https://127.0.0.1:1", "retry-code")
	if err != nil {
		t.Fatal(err)
	}
	if err = saveState(dir, state); err != nil {
		t.Fatal(err)
	}
	err = Run([]string{"register", "retry-code", "--reregister", "--state-dir", dir}, os.Stdin, io.Discard)
	if err == nil {
		t.Fatal("expected unavailable backend")
	}
	restored, err := loadState(dir)
	if err != nil {
		t.Fatal(err)
	}
	if restored.PrivateKey != state.PrivateKey || restored.RegistrationRequestID != state.RegistrationRequestID {
		t.Fatal("retry replaced pre-persisted identity")
	}
}

// Optional real-backend contract check. Credentials are supplied only to an isolated test environment.
func TestLiveBackend(t *testing.T) {
	server, code := os.Getenv("AGENT_TEST_SERVER"), os.Getenv("AGENT_TEST_CODE")
	if server == "" || code == "" {
		t.Skip("isolated backend not configured")
	}
	state, err := NewState(server, code)
	if err != nil {
		t.Fatal(err)
	}
	client := NewClient(server)
	state.Tokens, err = client.Register(state, code, "go-contract-fixture", "windows")
	if err != nil {
		t.Fatal(err)
	}
	recovered, err := client.Register(state, code, "go-contract-fixture", "windows")
	if err != nil || recovered.AccessToken != state.Tokens.AccessToken {
		t.Fatal("registration recovery failed")
	}
	state.RefreshRequestID, _ = newID()
	next, err := client.Refresh(state)
	if err != nil {
		t.Fatal(err)
	}
	recovered, err = client.Refresh(state)
	if err != nil || recovered.AccessToken != next.AccessToken {
		t.Fatal("refresh recovery failed")
	}
	var result map[string]any
	if err = client.request("GET", "/api/v1/agent/me", next.AccessToken, nil, &result); err != nil {
		t.Fatal(err)
	}
}
