package jp.xoki.web.zunda.smarthomewebserverbackend.common.constants;

/** APIアクセスログへ引き渡すリクエスト属性名。配置変更後も属性キーの既存値を維持する。 */
public final class ApiAccessLogAttributes {

	/** APIリクエストを識別するUUIDの属性名。 */
	public static final String REQUEST_ID =
			"jp.xoki.web.zunda.smarthomewebserverbackend.common.logging.ApiAccessLogAttributes.requestId";

	/** 共通エラー応答のエラーコード属性名。 */
	public static final String ERROR_CODE =
			"jp.xoki.web.zunda.smarthomewebserverbackend.common.logging.ApiAccessLogAttributes.errorCode";

	private ApiAccessLogAttributes() {
	}
}
