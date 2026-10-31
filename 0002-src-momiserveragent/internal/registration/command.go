package registration

import (
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"time"

	"golang.org/x/term"
)

// defaultDirectory はサービス実行アカウント間で共有するOS標準の保存先を返す。
// WindowsではProgramData未設定時に既定パスへ戻し、ディレクトリ作成や権限変更は行わない。
func defaultDirectory() string {
	switch runtime.GOOS {
	case "windows":
		base := os.Getenv("ProgramData")
		if base == "" {
			base = `C:\ProgramData`
		}
		return filepath.Join(base, "MomiServer", "agent")
	case "darwin":
		return "/Library/Application Support/MomiServer/agent"
	default:
		return "/var/lib/momiserver-agent"
	}
}

// Run は登録・認証更新・状態確認、または常駐実行を指定するCLI引数を処理する。
//
// argsにはプログラム名を含めず、inputとoutputには有効な非nilの入出力先を渡す。
// 登録コード省略時は端末から非表示入力し、保存先を排他ロックして鍵・要求ID・資格情報を更新する。
// 常駐処理は割り込み・SIGTERMで終了し、開始した処理の結果保存を待ってからロックを解放する。
// 入力・通信・保存の失敗はエラーで返す。プロセスの終了コード設定や入出力先のCloseは行わない。
func Run(args []string, input *os.File, output io.Writer) error {
	if len(args) > 0 && args[0] == "run" {
		return runDaemon(args[1:], output)
	}
	if len(args) == 0 || (args[0] != "register" && args[0] != "refresh" && args[0] != "status") {
		return errors.New("使用方法: momiserver-agent register [登録コード] / refresh / status / run [--state-dir PATH]（register: --server URL、開発時: --allow-insecure-localhost、runの開発MQTT: --allow-insecure-mqtt-localhost）")
	}
	command := args[0]
	rest := args[1:]
	code := ""
	if command == "register" && len(rest) > 0 && !strings.HasPrefix(rest[0], "-") {
		code, rest = rest[0], rest[1:]
	}
	flags := flag.NewFlagSet(command, flag.ContinueOnError)

	// flagのエラーや使用方法へ位置引数の登録コードが混入することを防ぐ。
	flags.SetOutput(io.Discard)
	server := flags.String("server", "", "backend base URL")
	directory := flags.String("state-dir", defaultDirectory(), "shared service state directory")
	reregister := flags.Bool("reregister", false, "replace local registration after administrative reset")
	allowLocalHTTP := flags.Bool("allow-insecure-localhost", false, "allow HTTP only to localhost, 127.0.0.1 or [::1] for development")
	if err := flags.Parse(rest); err != nil || flags.NArg() != 0 {
		return errors.New("コマンド引数を確認してください")
	}
	if *reregister && command != "register" {
		return errors.New("--reregisterはregisterのみで使用できます")
	}
	if command == "register" && code == "" {
		if !term.IsTerminal(int(input.Fd())) {
			return errors.New("登録コード未指定です。端末から対話入力するか引数で指定してください")
		}
		fmt.Fprint(output, "登録コード: ")
		value, err := term.ReadPassword(int(input.Fd()))
		fmt.Fprintln(output)
		if err != nil {
			return errors.New("登録コードを読み取れません")
		}
		code = strings.TrimSpace(string(value))
	}
	if command == "register" && (len(code) == 0 || len(code) > 100) {
		return errors.New("登録コードの長さが不正です")
	}
	dir, err := filepath.Abs(*directory)
	if err != nil {
		return errors.New("保存先パスが不正です")
	}
	if err = prepareDirectory(dir); err != nil {
		return err
	}
	// unlockはこのコマンドの終了時まで保持する、保存先全体の排他ロックを解放する。
	unlock, err := lockDirectory(dir)
	if err != nil {
		return err
	}
	defer unlock()
	state, err := loadState(dir)
	if err != nil {
		return err
	}
	if *server == "" {
		// configは接続先だけを補完する。資格情報は別の保護された状態ファイルから取得する。
		var config struct {
			Server string `json:"server"`
		}
		raw, readErr := os.ReadFile(filepath.Join(dir, "config.json"))
		if readErr == nil {
			if json.Unmarshal(raw, &config) != nil {
				return errors.New("config.jsonが不正です")
			}
			*server = config.Server
		} else if !os.IsNotExist(readErr) {
			return errors.New("config.jsonを読み取れません")
		}
		if *server == "" && state != nil {
			*server = state.Server
		}
	}
	resolved, err := serverURLWithOptions(*server, *allowLocalHTTP)
	if err != nil {
		return err
	}
	// 再登録の明示指定がない限り、保存済みの秘密値を異なる接続先へ送信しない。
	if state != nil && state.Server != resolved && !*reregister {
		return errors.New("保存済みの接続先と異なります。認証情報を別サーバーへ送信しません")
	}
	client := NewClient(resolved)
	if command == "register" {
		if state != nil && !*reregister && state.RegistrationCodeHash != digest(code) {
			return errors.New("異なる登録要求が保存されています。管理画面で再登録した場合は--reregisterを指定してください")
		}
		if state != nil && state.Tokens != nil && !*reregister {
			return errors.New("認証情報は保存済みです。statusで確認するか、管理画面で再登録後に--reregisterを指定してください")
		}
		if state == nil || (*reregister && (state.Tokens != nil || state.Server != resolved || state.RegistrationCodeHash != digest(code))) {
			state, err = NewState(resolved, code)
			if err != nil {
				return err
			}
			if err = saveState(dir, state); err != nil {
				return err
			}
		}
		hostname, err := os.Hostname()
		if err != nil {
			return errors.New("ホスト名を取得できません")
		}
		tokens, err := client.Register(state, code, hostname, runtime.GOOS)
		if err != nil {
			return err
		}
		state.Tokens = tokens
		if err = saveState(dir, state); err != nil {
			return err
		}
		fmt.Fprintf(output, "Agent登録情報を保存しました。Agent ID: %s\nMQTT接続設定はrunで取得します。\n", tokens.AgentID)
		return nil
	}
	if state == nil || state.Tokens == nil {
		return errors.New("先にAgentを登録してください")
	}
	// 応答喪失を復旧できるよう、未完了の更新要求IDを再利用し、新規IDは送信前に保存する。
	if command == "refresh" || state.RefreshRequestID != "" || time.Until(state.Tokens.AccessExpiresAt) < accessRefreshLead {
		if state.RefreshRequestID == "" {
			state.RefreshRequestID, err = newID()
			if err != nil {
				return err
			}
			if err = saveState(dir, state); err != nil {
				return err
			}
		}
		tokens, err := client.Refresh(state)
		if err != nil {
			return err
		}
		state.Tokens, state.RefreshRequestID = tokens, ""
		if err = saveState(dir, state); err != nil {
			return err
		}
	}
	// statusは秘密値を含めず表示できる、Backendでの認証・MQTT設定の確認結果を受け取る。
	var status struct {
		Status     string `json:"status"`
		MqttStatus string `json:"mqttStatus"`
	}
	if err = client.request("GET", "/api/v1/agent/me", state.Tokens.AccessToken, nil, &status); err != nil {
		return err
	}
	fmt.Fprintf(output, "Agent ID: %s\n登録状態: %s\n認証確認: 成功\nMQTT設定状態: %s\n", state.Tokens.AgentID, status.Status, status.MqttStatus)
	return nil
}
