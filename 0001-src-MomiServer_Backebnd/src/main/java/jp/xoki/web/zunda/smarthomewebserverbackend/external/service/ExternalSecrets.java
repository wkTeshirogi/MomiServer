package jp.xoki.web.zunda.smarthomewebserverbackend.external.service;

import java.security.SecureRandom;
import java.util.Base64;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.security.TokenHashes;
import jp.xoki.web.zunda.smarthomewebserverbackend.external.constants.ExternalApiConstants;

import static jp.xoki.web.zunda.smarthomewebserverbackend.external.constants.ExternalSecretsConstants.SECRET_BYTES;

/** 外部API用秘密値の生成と一方向ハッシュ。 */
final class ExternalSecrets {
	private static final SecureRandom RANDOM = new SecureRandom();

	private ExternalSecrets() {
	}

	static String generate() {
		byte[] bytes = new byte[SECRET_BYTES];
		RANDOM.nextBytes(bytes);
		return ExternalApiConstants.API_KEY_PREFIX + "_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	static String hash(String value) {
		return TokenHashes.sha256(value);
	}
}
