package jp.xoki.web.zunda.smarthomewebserverbackend.batch.service;

import jp.xoki.web.zunda.smarthomewebserverbackend.agent.service.AgentMqttProvisioning;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchExecutionRequestDetailDto;
import lombok.RequiredArgsConstructor;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.Map;

/** MQTT経由でAgentへバッチ実行コマンドを送信するService。 */
@Service
@RequiredArgsConstructor
public class BatchCommandPublishService {
	private final AgentMqttProvisioning agentMqttProvisioning;

	public boolean isEnabled() {
		return enabled && agentMqttProvisioning.isReady();
	}

	/** バッチ実行要求をMQTTメッセージへ変換するJSON Mapper。 */
	private final ObjectMapper objectMapper;

	/** MQTT通知の有効 / 無効。 */
	@Value("${mqtt.enabled:true}")
	private boolean enabled;

	/** MQTT Broker URL。 */
	@Value("${mqtt.broker-url}")
	private String brokerUrl;

	/** MQTT Client ID。 */
	@Value("${mqtt.client-id:smarthome-backend}")
	private String clientId;

	/** MQTT認証ユーザー名。 */
	@Value("${mqtt.username:}")
	private String username;

	/** MQTT認証パスワード。 */
	@Value("${mqtt.password:}")
	private String password;

	/** MQTT QoS。 */
	@Value("${mqtt.qos:1}")
	private int qos;

	/** MQTT接続タイムアウト秒数。 */
	@Value("${mqtt.connection-timeout:30}")
	private int connectionTimeout;

	/** MQTT Keep Alive間隔秒数。 */
	@Value("${mqtt.keep-alive-interval:60}")
	private int keepAliveInterval;

	/** MQTT自動再接続の有効 / 無効。 */
	@Value("${mqtt.automatic-reconnect:true}")
	private boolean automaticReconnect;

	@Value("${mqtt.topics.batch-cancel:smarthome/agents/{agentId}/batch/cancel}")
	private String batchCancelTopic;

	/** MQTT接続クライアント。 */
	private MqttClient client;
	/**
	 * バッチ実行要求をAgent向けMQTT Topicへ送信する。
	 *
	 * @param request バッチ実行要求
	 * @return 送信した場合はtrue、MQTT通知無効時はfalse
	 */
	public boolean publish(BatchExecutionRequestDetailDto request) {

		// MQTT通知が無効な場合は送信せず、呼び出し元に未送信として返す。
		if (!isEnabled()) {
			return false;
		}

		try {
			String topic = agentMqttProvisioning.executionTopic(request.getExecutionId(), "command");
            if (topic == null) return false;
			MqttMessage message = new MqttMessage(objectMapper.writeValueAsBytes(java.util.Map.of("execution_id", request.getExecutionId())));
			message.setQos(qos);
			message.setRetained(false);
			getClient().publish(topic, message);
			return true;
		} catch (JsonProcessingException | MqttException exception) {
			throw new IllegalStateException("Failed to publish batch command", exception);
		}
	}

	/** キャンセルは実行コマンドと別Topicへ同じ要求IDで再配信する。 */
	public boolean publishCancellation(BatchExecutionRequestDetailDto request) {
		if (!isEnabled()) return false;
		try {
            String topic = agentMqttProvisioning.executionTopic(request.getExecutionId(), "cancel");
            if (topic == null) return false;
			Map<String, Object> payload = java.util.Map.of(
					"command", "cancel",
					"execution_id", request.getExecutionId(),
					"cancel_requested_at", request.getCancelRequestedAt(),
					"cancel_grace_seconds", request.getCancelGraceSeconds());
			MqttMessage message = new MqttMessage(objectMapper.writeValueAsBytes(payload));
			message.setQos(qos);
			message.setRetained(false);
			getClient().publish(topic, message);
			return true;
		} catch (JsonProcessingException | MqttException exception) {
			throw new IllegalStateException("Failed to publish batch cancellation", exception);
		}
	}

	/** アプリケーション終了時にMQTT接続を切断する。 */
	@PreDestroy
	public synchronized void disconnect() throws MqttException {

		// 接続済みクライアントがある場合のみ切断する。
		if (client != null && client.isConnected()) {
			client.disconnect();
		}
	}

	/**
	 * MQTTクライアントを取得し、未接続の場合は接続する。
	 *
	 * @return 接続済みMQTTクライアント
	 * @throws MqttException MQTT接続に失敗した場合
	 */
	private synchronized MqttClient getClient() throws MqttException {

		// 初回送信時にMQTTクライアントを遅延生成する。
		if (client == null) {
			client = new MqttClient(
					brokerUrl,
					clientId + "-batch-command-publisher",
					new MemoryPersistence());
			client.setTimeToWait(Math.max(1, connectionTimeout) * 1000L);
		}

		// 切断状態の場合は現在の設定値で再接続する。
		if (!client.isConnected()) {
			client.connect(connectOptions());
		}
		return client;
	}

	/**
	 * MQTT接続オプションを設定値から生成する。
	 *
	 * @return MQTT接続オプション
	 */
	private MqttConnectOptions connectOptions() {
		MqttConnectOptions options = new MqttConnectOptions();
		options.setCleanSession(true);
		options.setConnectionTimeout(connectionTimeout);
		options.setKeepAliveInterval(keepAliveInterval);
		options.setAutomaticReconnect(automaticReconnect);

		// ユーザー名が指定されている場合のみ認証情報として設定する。
		if (username != null && !username.isBlank()) {
			options.setUserName(username);
		}

		// パスワードが指定されている場合のみ認証情報として設定する。
		if (password != null && !password.isBlank()) {
			options.setPassword(password.toCharArray());
		}
		return options;
	}
}
