package jp.xoki.web.zunda.smarthomewebserverbackend.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import jp.xoki.web.zunda.smarthomewebserverbackend.auth.constants.AuthMessageConstants;

/** トークン再発行APIで受け取るリフレッシュトークンのリクエストDTO。 */
@Getter
public class RefreshRequest {

	@NotBlank(message = AuthMessageConstants.REFRESH_REFRESH_TOKEN_REQUIRED)
	private String refreshToken;
}
