package jp.xoki.web.zunda.smarthomewebserverbackend.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

import java.util.Arrays;

/** 通知ルートの通知対象種別を表す列挙型。 */
@Getter
public enum NotificationTargetType {

	/** 通知専用ロールに紐づくユーザー全員を通知対象とする。 */
	NOTIFICATION_ROLE("notification_role"),

	/** 個別ユーザーを通知対象とする。 */
	USER("user");

	/** DB保存およびAPI入出力で利用する通知対象種別名。 */
	private final String storedName;

	NotificationTargetType(String storedName) {
		this.storedName = storedName;
	}

	/**
	 * API入力値またはDB保存名から通知対象種別へ変換する。
	 *
	 * @param value 通知対象種別名
	 * @return 対応する通知対象種別
	 */
	@JsonCreator
	public static NotificationTargetType from(String value) {

		// 未指定値は呼び出し元のValidationまたは既定値で扱う。
		if (value == null || value.isBlank()) {
			return null;
		}

		return Arrays.stream(values())
				.filter(type -> type.name().equalsIgnoreCase(value)
						|| type.storedName.equalsIgnoreCase(value))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unknown notification target type: " + value));
	}

	/**
	 * JSON出力時にDB保存名と同じ通知対象種別名を返す。
	 *
	 * @return JSONへ出力する通知対象種別名
	 */
	@JsonValue
	public String toJson() {
		return storedName;
	}
}
