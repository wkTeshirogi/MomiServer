package jp.xoki.web.zunda.smarthomewebserverbackend.user.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import static jp.xoki.web.zunda.smarthomewebserverbackend.user.constants.PersonalSettingsConstants.PASSWORD_MAX_BYTES;
import static jp.xoki.web.zunda.smarthomewebserverbackend.user.constants.PersonalSettingsConstants.PASSWORD_MIN_LENGTH;

/** 本人のパスワード変更入力。秘密情報を含むためtoStringは生成しない。 */
@Getter
@Setter
public class PersonalPasswordRequest {
    @NotBlank
    @Size(max = PASSWORD_MAX_BYTES)
    @JsonProperty("current_password")
    private String currentPassword;

    @NotBlank
    @Size(min = PASSWORD_MIN_LENGTH, max = PASSWORD_MAX_BYTES)
    @JsonProperty("new_password")
    private String newPassword;

    @NotBlank
    @Size(max = PASSWORD_MAX_BYTES)
    @JsonProperty("confirm_password")
    private String confirmPassword;
}
