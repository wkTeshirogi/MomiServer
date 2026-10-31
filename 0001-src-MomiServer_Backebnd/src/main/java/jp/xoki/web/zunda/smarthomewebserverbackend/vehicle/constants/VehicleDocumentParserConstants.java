package jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.constants;

import java.util.regex.Pattern;

/** VehicleDocumentParserで使用する固定値。カテゴリ内の利用元から参照する。 */
public final class VehicleDocumentParserConstants {

	public static final String VEHICLE_LABELS = "(?:自動車登録番号又は車両番号|登録番号|車両番号|車台番号|車名|原動機の型式|型式|初度登録年月|初度検査年月|"
			+ "登録年月日/交付年月日|交付年月日|有効期間の満了する日|車検有効期間満了日|有効期間満了日)";

	public static final Pattern DATE = Pattern.compile("(?:(令和|平成|昭和)\\s*(元|\\d{1,2})|(\\d{4}))"
			+ "[年/.-](\\d{1,2})[月/.-](\\d{1,2})日?");

	public static final Pattern MONTH = Pattern.compile("(?:(令和|平成|昭和)\\s*(元|\\d{1,2})|(\\d{4}))"
			+ "[年/-](\\d{1,2})月?");

	public static final String PERSONAL_DATA_LABELS = "(?:(?:所有者|使用者)(?:の)?\\s*"
			+ "(?:氏名又は名称|氏名|名称|住所)?|氏名又は名称|氏名|名称|住所)";

	public static final Pattern PERSONAL_DATA_BOUNDARY = Pattern.compile(
			"(?:^|\\s+)" + PERSONAL_DATA_LABELS + "\\s*[：:]?");

	public static final Pattern VEHICLE_DATA_BOUNDARY = Pattern.compile(
			"\\s+" + VEHICLE_LABELS + "\\s*[：:]?");

	public static final Pattern KNOWN_LABEL_AT_START = Pattern.compile(
			"^(?:" + VEHICLE_LABELS + "|" + PERSONAL_DATA_LABELS + ")\\s*[：:]?");

	private VehicleDocumentParserConstants() {
	}
}
