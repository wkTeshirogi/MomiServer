package jp.xoki.web.zunda.smarthomewebserverbackend.auth.dto.request;

import lombok.Getter;
import lombok.Setter;

/** ログアウトAPIで受け取るリフレッシュトークンのリクエストDTO。 */
@Getter
@Setter
public class LogoutRequest {

	/** 失効対象のリフレッシュトークン。 */
	private String refreshToken;
}
