package jp.xoki.web.zunda.smarthomewebserverbackend.common.error;

import jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.CommonMessageConstants;

/** 登録しようとしたメールアドレスが既に利用されている場合に使用する例外。 */
public class DuplicateEmailException extends RuntimeException {

	/**
	 * 重複したメールアドレスを含む例外メッセージを生成する。
	 *
	 * @param email 重複したメールアドレス
	 */
	public DuplicateEmailException(String email) {
		super(CommonMessageConstants.DUPLICATE_EMAIL.formatted(email));
	}
}
