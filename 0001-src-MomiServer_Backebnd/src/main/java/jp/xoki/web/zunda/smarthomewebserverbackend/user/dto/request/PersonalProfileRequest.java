package jp.xoki.web.zunda.smarthomewebserverbackend.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import static jp.xoki.web.zunda.smarthomewebserverbackend.user.constants.PersonalSettingsConstants.USERNAME_MAX_LENGTH;

/** 個人設定から変更できるプロフィール項目。対象ユーザーや権限は受け付けない。 */
public record PersonalProfileRequest(@NotBlank @Size(max = USERNAME_MAX_LENGTH) String username) {
}
