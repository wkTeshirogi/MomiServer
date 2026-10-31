package jp.xoki.web.zunda.smarthomewebserverbackend.agent.constants;

/** Agent Service階層で使用するDB項目名と基盤処理の定数。 */
public final class AgentServiceConstants {

	/** Mosquittoの明示許可ACLに設定する優先度。 */
	public static final int ALLOW_PRIORITY = 10;

	/** Agent登録状態または端末状態のDB項目名。 */
	public static final String FIELD_STATUS = "status";

	/** Agentの登録世代を識別するDB項目名。 */
	public static final String FIELD_GENERATION = "generation";

	/** 最終ハートビート受信日時のDB項目名。 */
	public static final String FIELD_HEARTBEAT_AT = "heartbeat_at";

	/** Agent外部UUIDの参照項目名。 */
	public static final String FIELD_AGENT_ID = "agent_id";

	/** Mosquitto制御APIのユーザー名項目。 */
	public static final String FIELD_USERNAME = "username";

	/** MQTT秘密値を暗号化・復号するときの用途識別接頭辞。 */
	public static final String MQTT_SECRET_CONTEXT = "mqtt:";

	private AgentServiceConstants() {
	}
}
