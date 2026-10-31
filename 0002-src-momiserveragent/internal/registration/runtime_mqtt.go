package registration

import (
	"errors"
	mqtt "github.com/eclipse/paho.mqtt.golang"
	"net/url"
	"strings"
	"sync/atomic"
	"time"
)

// mqttConnection はBackendが発行する、このAgent専用のMQTT接続設定を保持する。
type mqttConnection struct {
	// Enabled はMQTT連携が有効かどうかを表す。falseでは既存接続を閉じる。
	Enabled bool `json:"enabled"`
	// Status はBackend側の設定状態を表す。
	Status string `json:"status"`
	// Broker はTLSを基本とするブローカーURLを表す。
	Broker string `json:"brokerUrl"`
	// ClientID はこのAgentに割り当てられたMQTTクライアント識別子を表す。
	ClientID string `json:"clientId"`
	// Username はAgent専用のMQTT認証ユーザー名を保持する。
	Username string `json:"username"`
	// Password は接続用の秘密値を保持する。全体の表示・ログ出力は禁止する。
	Password string `json:"password"`
	// CommandTopic は実行要求の再照合を促す購読トピックを表す。
	CommandTopic string `json:"commandTopic"`
	// CancelTopic はキャンセル状態の再照合を促す購読トピックを表す。
	CancelTopic string `json:"cancelTopic"`
}

// runtimeBroker は常駐ループ所有のMQTT接続と、コールバックからの起床通知を保持する。
// コールバックはmessagesとwakeだけを操作する。wakeは閉じず、満杯の場合は通知をまとめる。
type runtimeBroker struct {
	messages   atomic.Uint64
	client     mqtt.Client
	settings   mqttConnection
	allowLocal bool
	wake       chan struct{}
}

// newRuntimeBroker は未接続のMQTT管理状態を返す。
// localは開発用ループバックTCPの許可を表す。利用終了時は呼び出し元がcloseする。
func newRuntimeBroker(local bool) *runtimeBroker {
	return &runtimeBroker{allowLocal: local, wake: make(chan struct{}, 1)}
}

// close は現在のMQTT接続を切断し、以後の接続状態参照から取り除く。
// 常駐ループから直列に呼び、接続コールバックと共有するwakeは閉じない。
func (b *runtimeBroker) close() {
	if b.client != nil {
		b.client.Disconnect(250)
		b.client = nil
	}
}

// connected は保持しているMQTT接続が開いているかを返す。購読完了は保証しない。
func (b *runtimeBroker) connected() bool { return b.client != nil && b.client.IsConnectionOpen() }

// configure は設定変更時に既存接続を閉じ、安全な接続先と必須資格情報を検証して接続する。
// 無効時は切断する。同一設定でクライアントを保持していれば再構築せず、自動再接続へ委ねる。
// 購読通知はHTTP再照合の契機としてだけ使用する。接続失敗はエラーで返し、呼び出し元が再試行する。
func (b *runtimeBroker) configure(c mqttConnection) error {
	if !c.Enabled {
		b.close()
		return nil
	}
	if b.settings == c && b.client != nil {
		return nil
	}
	b.close()
	u, err := url.Parse(c.Broker)
	if err != nil || u.Hostname() == "" || u.User != nil || u.RawQuery != "" || u.Fragment != "" || u.Path != "" {
		return errors.New("invalid MQTT endpoint")
	}
	if u.Scheme != "ssl" && !(u.Scheme == "tcp" && b.allowLocal && isLocalHost(u.Hostname())) {
		return errors.New("MQTTにはTLSが必要です。開発用ループバック接続のみ--allow-insecure-mqtt-localhostを指定できます")
	}
	if u.Scheme == "tcp" && strings.EqualFold(u.Hostname(), "localhost") {
		u.Host = strings.Replace(u.Host, u.Hostname(), "127.0.0.1", 1)
	}
	if c.Password == "" || c.Username == "" || c.ClientID == "" || c.CommandTopic == "" || c.CancelTopic == "" {
		return errors.New("incomplete MQTT configuration")
	}
	options := mqtt.NewClientOptions().AddBroker(u.String()).SetClientID(c.ClientID).SetUsername(c.Username).SetPassword(c.Password).
		SetCleanSession(true).SetAutoReconnect(true).SetConnectTimeout(10 * time.Second).SetWriteTimeout(10 * time.Second)
	options.SetOnConnectHandler(func(client mqtt.Client) {
		token := client.SubscribeMultiple(map[string]byte{c.CommandTopic: 1, c.CancelTopic: 1}, func(_ mqtt.Client, _ mqtt.Message) {

			// MQTT本文を実行命令として信用せず、HTTPで認証・世代・取消状態を再照合する契機としてだけ扱う。
			b.messages.Add(1)

			// 起床待ちが既にあれば通知をまとめる。コールバックを常駐ループの処理速度でブロックさせない。
			select {
			case b.wake <- struct{}{}:
			default:
			}
		})
		if token.WaitTimeout(10*time.Second) && token.Error() == nil {
			select {
			case b.wake <- struct{}{}:
			default:
			}
		}
	})
	client := mqtt.NewClient(options)
	token := client.Connect()
	if !token.WaitTimeout(12*time.Second) || token.Error() != nil {
		client.Disconnect(0)
		return errors.New("MQTT接続を確認できません")
	}
	b.client = client
	b.settings = c
	return nil
}
