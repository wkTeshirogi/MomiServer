package jp.xoki.web.zunda.smarthomewebserverbackend.scheduling.scheduler;

import static jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.ApplicationTimeConstants.ZONE_ID;

import jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.imports.VehicleImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 車両取込の期限切れデータ・原本を整理する定期実行入口。 */
@Component
@RequiredArgsConstructor
public class VehicleImportCleanupScheduler {

	private final VehicleImportService service;

	/** 期限切れ取込の整理。
	 *
	 * 別BeanのServiceを呼び出し、DB更新とコミット後の原本削除の境界を維持する。
	 */
	@Scheduled(cron = "${app.vehicle-import.cleanup-cron}", zone = ZONE_ID)
	public void cleanupExpired() {
		service.cleanupExpired();
	}
}
