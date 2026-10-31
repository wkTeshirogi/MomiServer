package jp.xoki.web.zunda.smarthomewebserverbackend.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

import java.util.Arrays;

/** ユーザー別ブラウザ通知の配信状態を表す列挙型。 */
@Getter
public enum NotificationDeliveryStatus {

	/** ブラウザ側で未配信または未表示の状態。 */
	PENDING("pending"),

	/** ブラウザ側へ配信または表示済みの状態。 */
	DELIVERED("delivered"),

	/** 配信または表示処理に失敗した状態。 */
	FAILED("failed");

	/** DB保存およびAPI入出力で利用する配信状態名。 */
	private final String storedName;

	NotificationDeliveryStatus(String storedName) {
		this.storedName = storedName;
	}

	/**
	 * API入力値またはDB保存名から配信状態へ変換する。
	 *
	 * @param value 配信状態名
	 * @return 対応する配信状態
	 */
	@JsonCreator
	public static NotificationDeliveryStatus from(String value) {

		// 未指定値は呼び出し元のValidationまたは既定値で扱う。
		if (value == null || value.isBlank()) {
			return null;
		}

		return Arrays.stream(values())
				.filter(status -> status.name().equalsIgnoreCase(value)
						|| status.storedName.equalsIgnoreCase(value))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unknown notification delivery status: " + value));
	}

	/**
	 * JSON出力時にDB保存名と同じ配信状態名を返す。
	 *
	 * @return JSONへ出力する配信状態名
	 */
	@JsonValue
	public String toJson() {
		return storedName;
	}
}
