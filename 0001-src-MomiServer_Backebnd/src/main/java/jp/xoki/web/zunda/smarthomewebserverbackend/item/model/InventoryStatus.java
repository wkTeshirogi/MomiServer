package jp.xoki.web.zunda.smarthomewebserverbackend.item.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

import java.util.Arrays;

/** 在庫状態を表す列挙型。 */
@Getter
public enum InventoryStatus {

	/** 利用可能な在庫状態。 */
	AVAILABLE("available"),

	/** 最低在庫数以下の在庫状態。 */
	LOW("low"),

	/** 在庫数量が0の在庫状態。 */
	OUT_OF_STOCK("out_of_stock"),

	/** 削除扱いの在庫状態。 */
	DELETED("deleted");

	/** DB保存およびAPI入出力で利用する在庫状態名。 */
	private final String storedName;

	InventoryStatus(String storedName) {
		this.storedName = storedName;
	}

	/**
	 * API入力値またはDB保存名から在庫状態へ変換する。
	 *
	 * @param value 在庫状態名
	 * @return 対応する在庫状態
	 */
	@JsonCreator
	public static InventoryStatus from(String value) {

		// 未指定値はBean Validation側で必須エラーとして扱う。
		if (value == null || value.isBlank()) {
			return null;
		}

		return Arrays.stream(values())
				.filter(status -> status.name().equalsIgnoreCase(value)
						|| status.storedName.equalsIgnoreCase(value))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unknown inventory status: " + value));
	}

	/**
	 * JSON出力時にDB保存名と同じ在庫状態名を返す。
	 *
	 * @return JSONへ出力する在庫状態名
	 */
	@JsonValue
	public String toJson() {
		return storedName;
	}
}
