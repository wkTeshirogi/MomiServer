package jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.model;

/** 車検期限と管理状態を表す値。 */
public enum VehicleInspectionStatus {

	/** 車検期限まで31日以上ある状態。 */
	VALID,

	/** 車検期限まで30日以内の状態。 */
	DUE_SOON,

	/** 車検期限まで7日以内の状態。 */
	URGENT,

	/** 車検期限を経過した状態。 */
	EXPIRED,

	/** 車検期限を判定できない状態。 */
	UNKNOWN,

	/** 管理対象外となった状態。 */
	ARCHIVED;

	/**
	 * DB保存値から車検状態へ変換する。
	 *
	 * @param value DB保存値
	 * @return 車検状態
	 */
	public static VehicleInspectionStatus fromStoredName(String value) {
		if (value == null || value.isBlank()) {
			return UNKNOWN;
		}
		return VehicleInspectionStatus.valueOf(value);
	}
}
