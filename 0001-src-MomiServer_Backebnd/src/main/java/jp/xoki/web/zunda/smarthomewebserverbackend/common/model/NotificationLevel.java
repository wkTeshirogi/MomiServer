package jp.xoki.web.zunda.smarthomewebserverbackend.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

import java.util.Arrays;

/** 通知レベルを表す列挙型。 */
@Getter
public enum NotificationLevel {

	/** 監査記録または軽微な情報通知。 */
	INFO("info"),

	/** 管理者の確認が望ましい警告通知。 */
	WARNING("warning"),

	/** 早急な確認が必要な重要通知。 */
	CRITICAL("critical");

	/** DB保存およびAPI入出力で利用する通知レベル名。 */
	private final String storedName;

	NotificationLevel(String storedName) {
		this.storedName = storedName;
	}

	/**
	 * Discord表示などで利用する大文字の通知レベル名を返す。
	 *
	 * @return 表示用の通知レベル名
	 */
	public String getLevel() {
		return name();
	}

	/**
	 * API入力値またはDB保存名から通知レベルへ変換する。
	 *
	 * @param value 通知レベル名
	 * @return 対応する通知レベル
	 */
	@JsonCreator
	public static NotificationLevel from(String value) {

		// 未指定値は呼び出し元のValidationまたは既定値で扱う。
		if (value == null || value.isBlank()) {
			return null;
		}

		return Arrays.stream(values())
				.filter(level -> level.name().equalsIgnoreCase(value)
						|| level.storedName.equalsIgnoreCase(value))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unknown notification level: " + value));
	}

	/**
	 * JSON出力時にDB保存名と同じ通知レベル名を返す。
	 *
	 * @return JSONへ出力する通知レベル名
	 */
	@JsonValue
	public String toJson() {
		return storedName;
	}
}
