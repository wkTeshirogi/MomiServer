package jp.xoki.web.zunda.smarthomewebserverbackend.common.error;

import jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.CommonMessageConstants;

/** 指定されたリソースが存在しない場合に使用する例外。 */
public class ResourceNotFoundException extends RuntimeException {

	/**
	 * リソース名と検索キーから例外メッセージを生成する。
	 *
	 * @param resourceName リソース名
	 * @param id 検索キー
	 */
	public ResourceNotFoundException(String resourceName, Object id) {
		super(CommonMessageConstants.RESOURCE_NOT_FOUND.formatted(resourceName, String.valueOf(id)));
	}
}
