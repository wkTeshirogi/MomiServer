package jp.xoki.web.zunda.smarthomewebserverbackend.agent.model;

/** AgentのMQTT同期・公開状態。端末の登録状態とは区別する。 */
public enum AgentMqttStatus {

	PENDING("pending"),
	READY("ready"),
	REVOKED("revoked"),
	REVOCATION_PENDING("revocation_pending"),
	NOT_CONFIGURED("not_configured");

	private final String storedName;

	AgentMqttStatus(String storedName) {
		this.storedName = storedName;
	}

	/** DBまたはAPIで使用する既存の小文字コードを返す。 */
	public String getStoredName() {
		return storedName;
	}
}
