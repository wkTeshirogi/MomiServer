package jp.xoki.web.zunda.smarthomewebserverbackend.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import jp.xoki.web.zunda.smarthomewebserverbackend.auth.constants.AuthMessageConstants;

/** ログインAPIで受け取るメールアドレスとパスワードのリクエストDTO。 */
@Getter
public class LoginRequest {
	@NotBlank(message = "")
	@Email(message = AuthMessageConstants.LOGIN_EMAIL_EMAIL)
	private String email;

	@NotBlank(message = AuthMessageConstants.LOGIN_PASSWORD_REQUIRED)
	@Size(min = 8, message = AuthMessageConstants.LOGIN_PASSWORD_SIZE)
	private String password;
}
