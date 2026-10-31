package jp.xoki.web.zunda.smarthomewebserverbackend.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** 全件取得用の安定した継続位置。総件数は取得完了した可視行から画面で算出する。 */
public record NotificationPageDto(List<UserBrowserNotificationDetailDto> items,
        @JsonProperty("next_cursor") String nextCursor) {
    public NotificationPageDto {
        items = List.copyOf(items);
    }
}
