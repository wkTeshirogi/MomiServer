package jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.imports;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.UUID;

/** 取込確認画面に返す候補値と状態。個人情報および抽出全文は含めない。 */
@Builder
public record VehicleImportDetailDto(UUID id, VehicleImportStatus status, String display_filename, String detected_media_type,
		String extraction_method, VehicleImportCandidate candidate, List<String> warnings,
		LocalDateTime expires_at, Long confirmed_vehicle_id) {
	public VehicleImportDetailDto {
		warnings = warnings == null ? null : Collections.unmodifiableList(new ArrayList<>(warnings));
	}
}
