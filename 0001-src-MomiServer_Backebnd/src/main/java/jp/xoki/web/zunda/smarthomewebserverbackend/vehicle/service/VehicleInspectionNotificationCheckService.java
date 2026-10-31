package jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.service;

import jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.repository.VehicleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import jp.xoki.web.zunda.jooq.tables.records.TVehiclesRecord;

/** 車検期限を日本時間で一括確認し、車両単位の通知Serviceへ委譲する。 */
@Service
@Slf4j
public class VehicleInspectionNotificationCheckService {
	private final VehicleRepository vehicles;
	private final VehicleInspectionNotificationService service;
	private final Clock clock;

	public VehicleInspectionNotificationCheckService(VehicleRepository vehicles, VehicleInspectionNotificationService service, Clock clock) {
		this.vehicles = vehicles;
		this.service = service;
		this.clock = clock;
	}

	/** 車検期限の日次確認
	 *
	 * 起動時にも現時点の通知段階を補完し、1台の失敗で他の車両の確認を止めない。
	 */
	public void checkExpiries() {
		LocalDate today = LocalDate.now(clock);
		for (TVehiclesRecord vehicle : vehicles.findAllActive()) {
			try {

				// 別Beanを呼び出し、車両ごとの通知・重複防止記録を同一トランザクションで確定する。
				service.notifyIfDue(vehicle.getId(), today);
			} catch (RuntimeException exception) {
				log.error("Vehicle inspection notification failed. vehicleId={}", vehicle.getId(), exception);
			}
		}
	}
}
