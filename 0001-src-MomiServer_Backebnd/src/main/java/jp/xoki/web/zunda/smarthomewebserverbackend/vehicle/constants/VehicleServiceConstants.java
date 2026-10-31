package jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.constants;

/** 車両Service階層に閉じるリソース識別と履歴種別の定数。 */
public final class VehicleServiceConstants {

	/** 車両未存在エラーで使用するリソース識別子。 */
	public static final String VEHICLE_RESOURCE = "vehicle";

	/** 車検期限の変更履歴種別。 */
	public static final String EXPIRY_UPDATED = "EXPIRY_UPDATED";

	private VehicleServiceConstants() {
	}
}
