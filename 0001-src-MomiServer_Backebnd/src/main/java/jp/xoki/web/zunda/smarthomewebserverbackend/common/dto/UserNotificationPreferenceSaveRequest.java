package jp.xoki.web.zunda.smarthomewebserverbackend.common.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

/** ユーザー別通知受信設定の保存リクエスト。 */
public record UserNotificationPreferenceSaveRequest(
		@Valid @NotEmpty List<UserNotificationPreferenceSaveDto> preferences
) {
	public UserNotificationPreferenceSaveRequest {
		preferences = preferences == null ? null : Collections.unmodifiableList(new ArrayList<>(preferences));
	}
}
