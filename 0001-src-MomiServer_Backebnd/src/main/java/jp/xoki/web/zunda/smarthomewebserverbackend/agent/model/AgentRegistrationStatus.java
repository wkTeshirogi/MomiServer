package jp.xoki.web.zunda.smarthomewebserverbackend.agent.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/** Agent登録状態。Enum名とDB・APIの保存コードを分離する。 */
public enum AgentRegistrationStatus {

	/** 登録コードの発行・登録を待つ状態。 */
	PENDING("pending"),

	/** 端末鍵の検証を終えて登録された状態。 */
	REGISTERED("registered"),

	/** 管理者によって無効化された状態。 */
	DISABLED("disabled");

	/** 既存のDB制約とAPI表現を維持する英語コード。 */
	private final String storedName;

	AgentRegistrationStatus(String storedName) {
		this.storedName = storedName;
	}

	/** 保存コード取得。
	 *
	 * Enum名に依存しない既存の小文字コードを返す。
	 * @return DB・APIで使用するコード
	 */
	@JsonValue
	public String getStoredName() {
		return storedName;
	}

	/** 保存コードからの復元。
	 *
	 * 既存の状態コードだけを許可し、不明な値は拒否する。
	 * @param value DB・API上のコード
	 * @return 対応する状態。未指定時はnull
	 */
	@JsonCreator
	public static AgentRegistrationStatus fromStoredName(String value) {
		if (value == null) {
			return null;
		}
		return Arrays.stream(values())
				.filter(status -> status.storedName.equals(value))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unknown Agent registration status"));
	}
}
