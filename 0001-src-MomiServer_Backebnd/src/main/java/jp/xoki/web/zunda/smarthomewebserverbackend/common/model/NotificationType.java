package jp.xoki.web.zunda.smarthomewebserverbackend.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

import java.util.Arrays;

/** ブラウザ通知の通知種別を表す列挙型。 */
@Getter
public enum NotificationType {

	/** セキュリティ関連の警告通知。 */
	SECURITY_ALERT("security_alert"),

	/** バッチ実行完了通知。 */
	BATCH_COMPLETED("batch_completed"),

	/** バッチ実行失敗通知。 */
	BATCH_FAILED("batch_failed"),

	/** 在庫不足通知。 */
	INVENTORY_LOW("inventory_low"),

	/** 車検有効期間の満了日接近・超過通知。 */
	VEHICLE_INSPECTION_EXPIRY("vehicle_inspection_expiry"),

	/** システム運用通知。 */
	SYSTEM_NOTICE("system_notice");

	/** DB保存およびAPI入出力で利用する通知種別名。 */
	private final String storedName;

	NotificationType(String storedName) {
		this.storedName = storedName;
	}

	/**
	 * API入力値またはDB保存名から通知種別へ変換する。
	 *
	 * @param value 通知種別名
	 * @return 対応する通知種別
	 */
	@JsonCreator
	public static NotificationType from(String value) {

		// 未指定値は呼び出し元のValidationまたは既定値で扱う。
		if (value == null || value.isBlank()) {
			return null;
		}

		return Arrays.stream(values())
				.filter(type -> type.name().equalsIgnoreCase(value)
						|| type.storedName.equalsIgnoreCase(value))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unknown notification type: " + value));
	}

	/**
	 * JSON出力時にDB保存名と同じ通知種別名を返す。
	 *
	 * @return JSONへ出力する通知種別名
	 */
	@JsonValue
	public String toJson() {
		return storedName;
	}
}
