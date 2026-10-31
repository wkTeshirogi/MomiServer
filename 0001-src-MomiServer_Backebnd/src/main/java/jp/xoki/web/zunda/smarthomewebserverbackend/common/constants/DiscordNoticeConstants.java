package jp.xoki.web.zunda.smarthomewebserverbackend.common.constants;

/** DiscordNoticeで使用する固定値。カテゴリ内の利用元から参照する。 */
public final class DiscordNoticeConstants {

	/** Discord通知に利用する赤色(猩々緋色)の埋め込みカラー。 */
	public static final int DISCORD_COLOR_CRITICAL = 0xE2041B;

	/** Discord通知に利用する黄色(蒲公英色)の埋め込みカラー。 */
	public static final int DISCORD_COLOR_WARNING = 0xFFD900;

	/** Discord通知に利用する緑色(若草色)の埋め込みカラー。 */
	public static final int DISCORD_COLOR_INFO = 0xC2D825;

	/** Discordメッセージ本文の最大文字数。 */
	public static final int DISCORD_CONTENT_MAX_LENGTH = 2000;

	/** Discord Embed descriptionの最大文字数。 */
	public static final int DISCORD_EMBED_DESCRIPTION_MAX_LENGTH = 4096;

	private DiscordNoticeConstants() {
	}
}
