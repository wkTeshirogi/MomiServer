package jp.xoki.web.zunda.smarthomewebserverbackend.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.RoleEnum;
import lombok.Getter;
import lombok.Setter;
import jp.xoki.web.zunda.smarthomewebserverbackend.auth.constants.AuthMessageConstants;

/** ユーザー設定変更APIで受け取る設定情報のリクエストDTO。 */
@Getter
@Setter
public class UserSettingsRequest {

	/** 設定変更対象のメールアドレスです。 */
	@NotBlank(message = AuthMessageConstants.USER_SETTINGS_EMAIL_REQUIRED)
	private String email;

	/** 変更後のパスワードです。未指定の場合は変更対象外として扱います。 */
	private String password;

	/** 変更後の権限です。Enum型のため空文字ではなくnullチェックを行います。 */
	@NotNull(message = AuthMessageConstants.USER_SETTINGS_ROLE_REQUIRED)
	private RoleEnum role;
}
