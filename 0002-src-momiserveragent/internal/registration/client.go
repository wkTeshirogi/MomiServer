package registration

import (
	"bytes"
	"context"
	"crypto/ed25519"
	"crypto/rand"
	"crypto/sha256"
	"crypto/x509"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/url"
	"strings"
	"time"
)

// Tokens はBackendが発行するAgent専用の資格情報と有効期限を保持する。
//
// ゼロ値は認証済み状態を表さない。トークンは秘密値のため、全体をログへ出力しない。
type Tokens struct {
	// AgentID は登録先で採番されたAgent識別子を表す。
	AgentID string `json:"agentId"`
	// DeviceID はAgentに紐づく端末マスタの識別子を表す。
	DeviceID int64 `json:"deviceId"`
	// Generation は再登録前の要求・資格情報と区別するための登録世代を表す。
	Generation int64 `json:"generation"`
	// TokenType はAuthorizationヘッダーで使用する認証方式を表す。
	TokenType string `json:"tokenType"`
	// AccessToken はAgent APIへ提示する短期トークンの原文を保持する。
	AccessToken string `json:"accessToken"`
	// AccessExpiresAt はアクセストークンの有効期限を表す。
	AccessExpiresAt time.Time `json:"accessExpiresAt"`
	// RefreshToken は資格情報の更新に使用するトークンの原文を保持する。
	RefreshToken string `json:"refreshToken"`
	// RefreshExpiresAt は更新トークンの有効期限を表す。
	RefreshExpiresAt time.Time `json:"refreshExpiresAt"`
	// MQTT は登録応答時点の連携状態を表す。実際の接続資格情報は常駐処理が別途取得する。
	MQTT struct {
		// Enabled はMQTT連携が有効かどうかを表す。
		Enabled bool `json:"enabled"`
		// Status はBackend側のMQTT設定状態を表す。
		Status string `json:"status"`
		// ClientID はこのAgentに割り当てられたMQTTクライアント識別子を表す。
		ClientID string `json:"clientId"`
	} `json:"mqtt"`
}

// State は応答喪失後も同じ要求を再送するための端末鍵・要求ID・資格情報を保持する。
//
// NewState で生成し、最初の送信より前に保存する。ゼロ値は無効で、並行更新には排他制御が必要となる。
type State struct {
	// Server は秘密値を送信してよい、検証済みのBackendベースURLを表す。
	Server string `json:"server"`
	// PrivateKey はBase64形式のEd25519秘密鍵を保持する。表示・ログ出力は禁止する。
	PrivateKey string `json:"privateKey"`
	// RegistrationRequestID は同じ登録要求の再試行で維持する識別子を表す。
	RegistrationRequestID string `json:"registrationRequestId"`
	// RegistrationCodeHash は入力コードとの照合用ハッシュで、コード原文の保存を避ける。
	RegistrationCodeHash string `json:"registrationCodeHash"`
	// RefreshRequestID は更新要求の送信前に保存する識別子で、成功の確定後にだけ空に戻す。
	RefreshRequestID string `json:"refreshRequestId,omitempty"`
	// Tokens は最後に確定した資格情報を保持する。nilは登録未完了を表す。
	Tokens *Tokens `json:"credentials,omitempty"`
}

// Client はAgent専用APIへ、秘密値を応答エラーへ含めずに要求を送る。
//
// NewClient で初期化し、通信開始後に設定を変更しない。State の保存・並行更新の同期は行わない。
type Client struct {
	// Server は検証済みで末尾スラッシュを除いたBackendベースURLを表す。
	Server string
	// HTTP は要求期限とリダイレクト禁止を設定した通信クライアントを保持する。
	HTTP *http.Client
}

// serverURL は開発用HTTPを許可せずにBackendのベースURLを検証する。
func serverURL(raw string) (string, error) {
	return serverURLWithOptions(raw, false)
}

// serverURLWithOptions は認証情報・クエリ・フラグメントを含まない接続先だけを許可する。
// allowLocalHTTP がtrueでもHTTPはループバックに限り、失敗時はURLを返さない。
func serverURLWithOptions(raw string, allowLocalHTTP bool) (string, error) {
	u, err := url.Parse(raw)
	if err != nil || u.Hostname() == "" || u.User != nil || u.RawQuery != "" || u.ForceQuery || u.Fragment != "" || u.Opaque != "" {
		return "", errors.New("バックエンドのベースURLを指定してください（認証情報・クエリ・フラグメント指定不可）")
	}
	// 開発用の明示許可とループバック指定の両方がある場合にだけTLS要件を緩和する。
	localHTTP := u.Scheme == "http" && allowLocalHTTP && isLocalHost(u.Hostname())
	if u.Scheme != "https" && !localHTTP {
		return "", errors.New("HTTPSで指定してください。開発用HTTPはlocalhost・127.0.0.1・[::1]に限り--allow-insecure-localhostが必要です")
	}
	return strings.TrimRight(u.String(), "/"), nil
}

// isLocalHost は開発用接続で許可するループバック名・アドレスとの完全一致を判定する。
func isLocalHost(host string) bool {
	return strings.EqualFold(host, "localhost") || host == "127.0.0.1" || host == "::1"
}

// NewClient は要求期限とリダイレクト禁止を設定したAgent APIクライアントを返す。
//
// server は呼び出し前に検証し、末尾スラッシュを除く必要がある。この関数自身はURLを拒否しない。
// ループバックHTTPではプロキシと名前解決を迂回し、登録コードやトークンの外部転送を防ぐ。
func NewClient(server string) *Client {
	client := &Client{Server: server, HTTP: &http.Client{
		Timeout: httpRequestTimeout,
		// 登録コードやトークンをリダイレクト先へ送信しない。
		CheckRedirect: func(_ *http.Request, _ []*http.Request) error { return http.ErrUseLastResponse },
	}}
	if u, err := url.Parse(server); err == nil && u.Scheme == "http" && isLocalHost(u.Hostname()) {
		transport := http.DefaultTransport.(*http.Transport).Clone()

		// 開発用の秘密値を環境プロキシやDNSで別の接続先へ送らない。
		transport.Proxy = nil
		transport.DialContext = func(ctx context.Context, network, address string) (net.Conn, error) {
			host, port, err := net.SplitHostPort(address)
			if err != nil || !isLocalHost(host) {
				return nil, errors.New("開発用HTTPの接続先がループバックではありません")
			}
			if strings.EqualFold(host, "localhost") {
				host = "127.0.0.1"
			}
			dialer := net.Dialer{Timeout: httpRequestTimeout}
			return dialer.DialContext(ctx, network, net.JoinHostPort(host, port))
		}
		client.HTTP.Transport = transport
	}
	return client
}

// digest は登録コードや更新トークンを照合・署名用のSHA-256小文字16進文字列へ変換する。
func digest(value string) string {
	hash := sha256.Sum256([]byte(value))
	return hex.EncodeToString(hash[:])
}

// newID は再送の同一性を識別するUUID形式のランダムな要求IDを返す。
// 乱数生成に失敗した場合は不完全な識別子を返さない。
func newID() (string, error) {
	var bytes [16]byte
	if _, err := rand.Read(bytes[:]); err != nil {
		return "", errors.New("要求IDを生成できません")
	}
	bytes[6] = (bytes[6] & 0x0f) | 0x40
	bytes[8] = (bytes[8] & 0x3f) | 0x80
	return fmt.Sprintf("%x-%x-%x-%x-%x", bytes[:4], bytes[4:6], bytes[6:8], bytes[8:10], bytes[10:]), nil
}

// NewState は端末鍵と登録要求IDを生成し、未登録の状態を返す。
//
// server は検証済みの接続先とし、code は原文を保持せず照合用ハッシュだけを保存する。
// ファイル保存や通信は行わない。呼び出し元は登録前に結果を保護領域へ保存する。
// 鍵・要求IDの生成失敗時はnilと秘密値を含まないエラーを返す。
func NewState(server, code string) (*State, error) {
	_, private, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		return nil, errors.New("端末鍵を生成できません")
	}
	id, err := newID()
	if err != nil {
		return nil, err
	}
	return &State{Server: server, PrivateKey: base64.StdEncoding.EncodeToString(private),
		RegistrationRequestID: id, RegistrationCodeHash: digest(code)}, nil
}

// request は信頼済みの相対APIパスへJSON要求を送り、必要なら応答をtargetへ復元する。
// body・targetがnilの場合は、それぞれ要求本文・応答復元を省略する。応答Bodyは内部で閉じる。
// 非2xx・通信失敗・復元失敗は秘密値を含まないエラーへ置換するため、元エラーの識別は保証しない。
// contextのキャンセルは受け取らず、HTTPクライアントの要求期限で通信を制限する。
func (c *Client) request(method, path, token string, body any, target any) error {
	// payloadはJSON化した要求本文であり、エラーやログへ含めない。
	var payload []byte
	var err error
	if body != nil {
		payload, err = json.Marshal(body)
		if err != nil {
			return errors.New("送信情報を作成できません")
		}
	}
	req, err := http.NewRequest(method, c.Server+path, bytes.NewReader(payload))
	if err != nil {
		return errors.New("送信先を確認してください")
	}
	req.Header.Set("Content-Type", "application/json")
	if token != "" {
		req.Header.Set("Authorization", "Bearer "+token)
	}
	resp, err := c.HTTP.Do(req)
	if err != nil {
		return errors.New("バックエンドへ接続できません。保存済みの要求情報を使って再試行できます")
	}
	defer resp.Body.Close()
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		// 中継サーバーが秘密値を反射する可能性があるため、応答本文はエラーへ転載しない。
		switch resp.StatusCode {
		case 401:
			return errors.New("認証できません。コード・認証情報の期限と登録状態を確認してください")
		case 409:
			return errors.New("登録状態を確認してください。復旧期限切れの場合は管理画面から再登録してください")
		case 429:
			return errors.New("試行回数の上限です。1分以上空けて再試行してください")
		case 503:
			return errors.New("バックエンドのAgent認証設定が未構成です")
		default:
			return fmt.Errorf("バックエンドがHTTP %dを返しました", resp.StatusCode)
		}
	}
	if target == nil {
		return nil
	}
	if err := json.NewDecoder(io.LimitReader(resp.Body, 2*1024*1024)).Decode(target); err != nil {
		return errors.New("応答を読み取れません。同じ操作を再試行してください")
	}
	return nil
}

// proof はBackendのnonceと保存済み端末鍵で要求用途・ID・資格情報ハッシュに署名する。
// stateは非nilを前提とし、不正なnonce・秘密鍵・通信失敗時は署名を返さない。
func (c *Client) proof(state *State, purpose, id, credential string) (string, error) {
	var challenge struct {
		Nonce string `json:"nonce"`
	}
	if err := c.request(http.MethodPost, "/api/v1/agent/auth/challenge", "", nil, &challenge); err != nil {
		return "", err
	}
	if challenge.Nonce == "" || len(challenge.Nonce) > 100 {
		return "", errors.New("認証確認値が不正です")
	}
	private, err := base64.StdEncoding.DecodeString(state.PrivateKey)
	if err != nil || len(private) != ed25519.PrivateKeySize {
		return "", errors.New("保存済みの端末鍵が不正です")
	}
	payload, err := json.Marshal(map[string]string{"aud": "momiserver-agent-" + purpose,
		"nonce": challenge.Nonce, "requestId": id, "credentialHash": digest(credential)})
	if err != nil {
		return "", errors.New("署名情報を作成できません")
	}
	header := base64.RawURLEncoding.EncodeToString([]byte(`{"alg":"EdDSA","typ":"JWT"}`))
	signing := header + "." + base64.RawURLEncoding.EncodeToString(payload)
	signature := ed25519.Sign(ed25519.PrivateKey(private), []byte(signing))
	return signing + "." + base64.RawURLEncoding.EncodeToString(signature), nil
}

// validTokens は非nilの応答について、識別情報・接頭辞・更新期限の基本整合性を確認する。
// Backendの署名検証やアクセストークンの失効照会を代行するものではない。
func validTokens(tokens *Tokens) bool {
	return tokens.AgentID != "" && tokens.DeviceID > 0 && tokens.Generation > 0 && tokens.TokenType == "Bearer" &&
		strings.HasPrefix(tokens.AccessToken, "ma_") && strings.HasPrefix(tokens.RefreshToken, "mr_") &&
		!tokens.AccessExpiresAt.IsZero() && tokens.RefreshExpiresAt.After(time.Now())
}

// Register は端末鍵の保有証明を付けて登録し、検証済みの資格情報を返す。
//
// stateはNewStateで生成・保存済みとし、再試行でも同じ鍵・要求ID・codeを使う。
// state自体の変更や保存は行わないため、呼び出し元が成功後に資格情報を保存する。
// 署名・通信・応答検証の失敗時はnilを返す。hostnameとosNameは端末の登録属性として送信する。
func (c *Client) Register(state *State, code, hostname, osName string) (*Tokens, error) {
	proof, err := c.proof(state, "register", state.RegistrationRequestID, code)
	if err != nil {
		return nil, err
	}
	// 同じ呼び出し中のproofで鍵の復元と長さを検証済みのため、ここでは復元エラーを再判定しない。
	private, _ := base64.StdEncoding.DecodeString(state.PrivateKey)
	publicDER, err := x509.MarshalPKIXPublicKey(ed25519.PrivateKey(private).Public())
	if err != nil {
		return nil, errors.New("公開鍵を作成できません")
	}
	body := map[string]string{"registrationCode": code, "registrationRequestId": state.RegistrationRequestID,
		"publicKey": base64.StdEncoding.EncodeToString(publicDER), "proof": proof,
		"hostname": hostname, "os": osName, "agentVersion": version}
	var tokens Tokens
	if err := c.request(http.MethodPost, "/api/v1/agent/register", "", body, &tokens); err != nil {
		return nil, err
	}
	if !validTokens(&tokens) {
		return nil, errors.New("登録応答が不正です。同じ登録コードで再試行してください")
	}
	return &tokens, nil
}

// Refresh は保存済みの更新要求IDと端末鍵の保有証明で資格情報を更新する。
//
// stateは非nilで、Tokensと送信前に永続化したRefreshRequestIDを持つ必要がある。
// 応答喪失時も同じ要求IDで再試行し、成功後の資格情報保存と要求ID解除は呼び出し元で行う。
// stateは変更しない。更新失敗やAgent・端末・世代の不一致時はnilとエラーを返す。
func (c *Client) Refresh(state *State) (*Tokens, error) {
	if state.Tokens == nil || state.RefreshRequestID == "" {
		return nil, errors.New("保存済みの更新要求が必要です")
	}
	proof, err := c.proof(state, "refresh", state.RefreshRequestID, state.Tokens.RefreshToken)
	if err != nil {
		return nil, err
	}
	body := map[string]string{"refreshToken": state.Tokens.RefreshToken, "refreshRequestId": state.RefreshRequestID, "proof": proof}
	var tokens Tokens
	if err := c.request(http.MethodPost, "/api/v1/agent/auth/refresh", "", body, &tokens); err != nil {
		return nil, err
	}
	if !validTokens(&tokens) || tokens.AgentID != state.Tokens.AgentID || tokens.Generation != state.Tokens.Generation ||
		tokens.DeviceID != state.Tokens.DeviceID {
		return nil, errors.New("認証更新応答が不正です")
	}
	return &tokens, nil
}
