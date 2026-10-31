package jp.xoki.web.zunda.smarthomewebserverbackend.item.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

import java.util.Arrays;

/** 在庫数量の変更種別を表す列挙型。 */
@Getter
public enum InventoryStockChangeType {

	/** 入庫による在庫増加。 */
	INBOUND("inbound"),

	/** 出庫による在庫減少。 */
	OUTBOUND("outbound"),

	/** 棚卸による在庫数量の絶対値更新。 */
	STOCKTAKE("stocktake"),

	/** 廃棄による在庫減少。 */
	DISCARD("discard"),

	/** 在庫行の削除扱い。 */
	DELETE("delete");

	/** DB保存およびAPI入出力で利用する変更種別名。 */
	private final String storedName;

	InventoryStockChangeType(String storedName) {
		this.storedName = storedName;
	}

	/**
	 * API入力値またはDB保存名から在庫操作種別へ変換する。
	 *
	 * @param value 在庫操作種別名
	 * @return 対応する在庫操作種別
	 */
	@JsonCreator
	public static InventoryStockChangeType from(String value) {

		// 未指定値はBean Validation側で必須エラーとして扱う。
		if (value == null || value.isBlank()) {
			return null;
		}

		return Arrays.stream(values())
				.filter(type -> type.name().equalsIgnoreCase(value)
						|| type.storedName.equalsIgnoreCase(value))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unknown inventory stock change type: " + value));
	}

	/**
	 * JSON出力時にDB保存名と同じ変更種別名を返す。
	 *
	 * @return JSONへ出力する変更種別名
	 */
	@JsonValue
	public String toJson() {
		return storedName;
	}
}
