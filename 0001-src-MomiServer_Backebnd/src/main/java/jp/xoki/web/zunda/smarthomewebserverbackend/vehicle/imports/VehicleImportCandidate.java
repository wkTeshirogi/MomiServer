package jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.imports;

import lombok.Builder;

import java.time.LocalDate;

/** 車検証から保守的に抽出した、確認画面だけで使用する車両候補。 */
@Builder
public record VehicleImportCandidate(String display_name, String registration_number, String chassis_number,
		String vehicle_name, String model_code, String first_registration_month,
		LocalDate inspection_expiry_date, LocalDate issued_date) {
}
