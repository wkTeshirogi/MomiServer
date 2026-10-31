package jp.xoki.web.zunda.smarthomewebserverbackend.scheduling.scheduler;

import static jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.ApplicationTimeConstants.ZONE_ID;

import jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.service.VehicleInspectionNotificationCheckService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 車検期限通知の日次処理・起動時補完の入口。 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.vehicle-inspection-notification", name = "enabled", havingValue = "true", matchIfMissing = true)
public class VehicleInspectionNotificationScheduler {

	private final VehicleInspectionNotificationCheckService service;

	/** 車検期限の一括確認。
	 *
	 * 従来と同じ日本時間の日次Cronと起動完了イベントから業務Serviceを呼び出す。
	 */
	@Scheduled(cron = "${app.vehicle-inspection-notification.cron:0 0 9 * * *}", zone = ZONE_ID)
	@EventListener(ApplicationReadyEvent.class)
	public void checkExpiries() {
		service.checkExpiries();
	}
}
