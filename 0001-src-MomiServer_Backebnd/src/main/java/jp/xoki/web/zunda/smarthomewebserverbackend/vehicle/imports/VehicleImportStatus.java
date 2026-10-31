package jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.imports;

/** 車両取込の確認・終了状態。 */
public enum VehicleImportStatus {
	PENDING_REVIEW, REGISTERED, CANCELLED, PARSE_FAILED, EXPIRED
}
