package jp.xoki.web.zunda.smarthomewebserverbackend.common.constants;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

import java.util.Arrays;

/** アプリケーションで利用するユーザー権限を表す列挙型。 */
@Getter
public enum RoleEnum {

	/** 管理者権限です。 */
	ADMIN_ROLE(999, "ADMIN"),

	/** 日常運用操作を行うユーザー権限です。 */
	OPERATOR_ROLE(100, "OPERATOR"),

	/** 一般ユーザー権限です。 */
	USER_ROLE(10, "USER"),

	/** アクセス禁止ユーザー権限です。 */
	BAN_USER_ROLE(0, "BAN_USER");

	/** 権限の優先度・重みとして利用する値です。 */
	private final int value;

	/** DB保存およびAPI入出力で利用する権限名です。 */
	private final String storedName;

	RoleEnum(int value, String storedName) {
		this.value = value;
		this.storedName = storedName;
	}

	/**
	 * API入力値またはDB保存名から権限列挙値へ変換する。
	 *
	 * @param value 権限名
	 * @return 対応する権限
	 */
	@JsonCreator
	public static RoleEnum from(String value) {

		// 未指定値は呼び出し元のValidationまたは既定値で扱う。
		if (value == null) {
			return null;
		}
		return Arrays.stream(values())
				.filter(role -> role.name().equalsIgnoreCase(value)
						|| role.storedName.equalsIgnoreCase(value))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unknown role: " + value));
	}

	/**
	 * JSON出力時にDB保存名と同じ権限名を返す。
	 *
	 * @return JSONへ出力する権限名
	 */
	@JsonValue
	public String toJson() {
		return storedName;
	}
}
