package jp.xoki.web.zunda.smarthomewebserverbackend.batch.model;

/** バッチ添付の状態。定義の状態とは別の保存コードとして管理する。 */
public enum BatchFileStatus {

	ACTIVE("active"),
	DELETED("deleted");

	private final String storedName;

	BatchFileStatus(String storedName) {
		this.storedName = storedName;
	}

	/** 既存DBの制約と一致する保存コードを返す。 */
	public String getStoredName() {
		return storedName;
	}
}
