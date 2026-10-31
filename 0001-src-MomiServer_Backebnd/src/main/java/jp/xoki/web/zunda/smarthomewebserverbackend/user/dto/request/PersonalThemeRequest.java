package jp.xoki.web.zunda.smarthomewebserverbackend.user.dto.request;

import jakarta.validation.constraints.NotNull;
import jp.xoki.web.zunda.smarthomewebserverbackend.user.model.UserTheme;

/** 個人設定のテーマ変更入力。 */
public record PersonalThemeRequest(@NotNull UserTheme theme) {
}
