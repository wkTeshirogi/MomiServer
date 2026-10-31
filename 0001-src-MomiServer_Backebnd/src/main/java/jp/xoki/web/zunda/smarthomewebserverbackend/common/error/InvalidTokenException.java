package jp.xoki.web.zunda.smarthomewebserverbackend.common.error;

/** JWTが無効な場合に使用する例外。 */
public class InvalidTokenException extends RuntimeException {

	/**
	 * メッセージを指定して例外を生成する。
	 *
	 * @param message 例外メッセージ
	 */
	public InvalidTokenException(String message) { super(message); }

	/**
	 * 原因例外を保持して例外を生成する。
	 *
	 * @param message 例外メッセージ
	 * @param cause 原因例外
	 */
	public InvalidTokenException(String message, Throwable cause) { super(message, cause); }
}
