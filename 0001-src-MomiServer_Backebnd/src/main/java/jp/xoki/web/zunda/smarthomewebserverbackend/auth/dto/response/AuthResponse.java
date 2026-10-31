package jp.xoki.web.zunda.smarthomewebserverbackend.auth.dto.response;

import lombok.Builder;
import lombok.Getter;

/** 認証成功時に返すアクセストークン、リフレッシュトークン、有効期限のレスポンスDTO。 */
@Getter
@Builder
public class AuthResponse {
	private String accessToken;
	private String refreshToken;
	private long expiresnIn;
}
