package jp.xoki.web.zunda.smarthomewebserverbackend.common.error;

/** 更新番号の不一致による競合を表す例外。 */
public class OptimisticLockConflictException extends RuntimeException {

	/**
	 * 競合例外を生成する。
	 *
	 * @param message 利用者へ返すメッセージ
	 */
	public OptimisticLockConflictException(String message) {
		super(message);
	}
}
