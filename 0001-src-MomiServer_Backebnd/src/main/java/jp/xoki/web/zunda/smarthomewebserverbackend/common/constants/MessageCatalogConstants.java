package jp.xoki.web.zunda.smarthomewebserverbackend.common.constants;

/** MessageCatalogで使用する固定値。カテゴリ内の利用元から参照する。 */
public final class MessageCatalogConstants {

	/** 共通メッセージカタログのクラスパス上の配置先。 */
	public static final String CATALOG_PATH = "message-catalog/messages.ja.json";

	/** メッセージ内のプレースホルダー開始文字。 */
	public static final String PLACEHOLDER_PREFIX = "{";

	/** メッセージ内のプレースホルダー終了文字。 */
	public static final String PLACEHOLDER_SUFFIX = "}";

	private MessageCatalogConstants() {
	}
}
