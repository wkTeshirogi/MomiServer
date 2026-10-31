package jp.xoki.web.zunda.smarthomewebserverbackend.common.model;

import lombok.Getter;

import java.util.Arrays;

@Getter
public enum NotificationRoleCode {
	ALL("ROLE_ALL"),
	ITEM_STOCK("ROLE_ITEM_STOCK"),;

	private final String storedName;

	NotificationRoleCode(String storedName) {
		this.storedName = storedName;
	}

	/**
	 * API入力値またはDB保存名から通知ロールコードへ変換する。
	 *
	 * @param value 通知ロールコード
	 * @return 対応する通知ロールコード
	 */
	public static NotificationRoleCode from(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}

		return Arrays.stream(values())
				.filter(role -> role.name().equalsIgnoreCase(value)
						|| role.storedName.equalsIgnoreCase(value))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unknown notification role code: " + value));
	}
}
