package jp.xoki.web.zunda.smarthomewebserverbackend.user.dto.response;

import jp.xoki.web.zunda.smarthomewebserverbackend.user.model.UserTheme;
import com.fasterxml.jackson.annotation.JsonInclude;

/** 本人の表示設定。パスワード、権限変更用項目、他ユーザーの情報は返さない。 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record PersonalSettingsDto(String username, UserTheme theme) {
}
