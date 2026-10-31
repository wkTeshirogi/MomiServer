package jp.xoki.web.zunda.smarthomewebserverbackend.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.RoleEnum;
import lombok.Getter;
import lombok.Setter;
import jp.xoki.web.zunda.smarthomewebserverbackend.auth.constants.AuthMessageConstants;

/** ユーザー登録APIで受け取るユーザー情報のリクエストDTO。 */
@Getter
@Setter
public class RegisterRequest {

	/** ログインIDとして利用するメールアドレスです。 */
	@NotBlank(message = AuthMessageConstants.REGISTER_EMAIL_REQUIRED)
	@Email(message = AuthMessageConstants.REGISTER_EMAIL_EMAIL)
	private String email;

	/** ユーザーがログイン時に利用するパスワードです。 */
//	@NotBlank(message = "パスワードは必須です")
//	@Size(min = 8, message = "パスワードは8文字以上で入力してください")
	private String password;

	/** 登録するユーザーの権限です。 */
	@NotNull(message = AuthMessageConstants.REGISTER_ROLE_REQUIRED)
	private RoleEnum role;
}
