package jp.xoki.web.zunda.smarthomewebserverbackend.common.message;

import java.util.Arrays;
import java.util.Optional;

/** バックエンドとフロントエンドで共有するメッセージコードを表す列挙型。 */
public enum MessageCode {

	/** 在庫不足通知のタイトル。 */
	INVENTORY_LOW_TITLE("inventory.low.title"),

	/** 在庫不足通知の本文。 */
	INVENTORY_LOW_MESSAGE("inventory.low.message"),

	/** 在庫切れ通知のタイトル。 */
	INVENTORY_OUT_OF_STOCK_TITLE("inventory.outOfStock.title"),

	/** 在庫切れ通知の本文。 */
	INVENTORY_OUT_OF_STOCK_MESSAGE("inventory.outOfStock.message"),

	/** 入力内容確認を促す汎用メッセージ。 */
	VALIDATION_FORM_CHECK_INPUT("validation.form.checkInput"),

	/** 在庫品目必須エラーメッセージ。 */
	VALIDATION_INVENTORY_ITEM_REQUIRED("validation.inventory.itemRequired"),

	/** 保管場所必須エラーメッセージ。 */
	VALIDATION_INVENTORY_STORAGE_LOCATION_REQUIRED("validation.inventory.storageLocationRequired"),

	/** 現在数量下限エラーメッセージ。 */
	VALIDATION_INVENTORY_QUANTITY_MIN("validation.inventory.quantityMin"),

	/** 補充数量下限エラーメッセージ。 */
	VALIDATION_INVENTORY_REPLENISH_QUANTITY_MIN("validation.inventory.replenishQuantityMin"),

	/** 在庫ステータス必須エラーメッセージ。 */
	VALIDATION_INVENTORY_STATUS_REQUIRED("validation.inventory.statusRequired"),

	/** 在庫ID不正エラーメッセージ。 */
	ERROR_INVENTORY_INVALID_ID("error.inventory.invalidId"),

	/** 在庫情報未検出エラーメッセージ。 */
	ERROR_INVENTORY_NOT_FOUND("error.inventory.notFound"),

	/** 在庫詳細取得失敗エラーメッセージ。 */
	ERROR_INVENTORY_FETCH_DETAIL_FAILED("error.inventory.fetchDetailFailed");

	/** 共通メッセージカタログ上のキー。 */
	private final String key;

	MessageCode(String key) {
		this.key = key;
	}

	/**
	 * 共通メッセージカタログ上のキーを返す。
	 *
	 * @return メッセージキー
	 */
	public String getKey() {
		return key;
	}

	/**
	 * 共通メッセージカタログのキーからメッセージコードへ変換する。
	 *
	 * @param key メッセージキー
	 * @return 対応するメッセージコード
	 */
	public static Optional<MessageCode> fromKey(String key) {
		return Arrays.stream(values())
				.filter(code -> code.key.equals(key))
				.findFirst();
	}
}
