package jp.xoki.web.zunda.smarthomewebserverbackend.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

import java.util.Arrays;

/** ユーザー別ブラウザ通知の既読状態を表す列挙型。 */
@Getter
public enum NotificationReadStatus {

	/** 未読状態。 */
	UNREAD("unread"),

	/** 既読状態。 */
	READ("read"),

	/** ユーザーが非表示にした状態。 */
	DISMISSED("dismissed");

	/** DB保存およびAPI入出力で利用する既読状態名。 */
	private final String storedName;

	NotificationReadStatus(String storedName) {
		this.storedName = storedName;
	}

	/**
	 * API入力値またはDB保存名から既読状態へ変換する。
	 *
	 * @param value 既読状態名
	 * @return 対応する既読状態
	 */
	@JsonCreator
	public static NotificationReadStatus from(String value) {

		// 未指定値は呼び出し元のValidationまたは既定値で扱う。
		if (value == null || value.isBlank()) {
			return null;
		}

		return Arrays.stream(values())
				.filter(status -> status.name().equalsIgnoreCase(value)
						|| status.storedName.equalsIgnoreCase(value))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unknown notification read status: " + value));
	}

	/**
	 * JSON出力時にDB保存名と同じ既読状態名を返す。
	 *
	 * @return JSONへ出力する既読状態名
	 */
	@JsonValue
	public String toJson() {
		return storedName;
	}
}
