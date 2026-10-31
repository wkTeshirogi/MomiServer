package jp.xoki.web.zunda.smarthomewebserverbackend.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

import java.util.Arrays;

/** ブラウザ通知の発生元種別を表す列挙型。 */
@Getter
public enum NotificationSourceType {

	/** セキュリティ監査イベントを発生元とする通知。 */
	SECURITY_EVENT("security_event"),

	/** バッチ実行を発生元とする通知。 */
	BATCH_EXECUTION("batch_execution"),

	/** 在庫管理を発生元とする通知。 */
	INVENTORY("inventory"),

	/** 車両管理を発生元とする通知。 */
	VEHICLE("vehicle"),

	/** システム共通を発生元とする通知。 */
	SYSTEM("system");

	/** DB保存およびAPI入出力で利用する発生元種別名。 */
	private final String storedName;

	NotificationSourceType(String storedName) {
		this.storedName = storedName;
	}

	/**
	 * API入力値またはDB保存名から通知発生元種別へ変換する。
	 *
	 * @param value 通知発生元種別名
	 * @return 対応する通知発生元種別
	 */
	@JsonCreator
	public static NotificationSourceType from(String value) {

		// 未指定値は呼び出し元のValidationまたは既定値で扱う。
		if (value == null || value.isBlank()) {
			return null;
		}

		return Arrays.stream(values())
				.filter(type -> type.name().equalsIgnoreCase(value)
						|| type.storedName.equalsIgnoreCase(value))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unknown notification source type: " + value));
	}

	/**
	 * JSON出力時にDB保存名と同じ通知発生元種別名を返す。
	 *
	 * @return JSONへ出力する通知発生元種別名
	 */
	@JsonValue
	public String toJson() {
		return storedName;
	}
}
