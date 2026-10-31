package jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 車検期限の登録・変更・管理終了履歴を返却するDTO。 */
@Getter
@Builder
public class VehicleInspectionHistoryDto {

	private Long id;
	@JsonProperty("vehicle_id")
	private Long vehicleId;
	@JsonProperty("change_type")
	private String changeType;
	@JsonProperty("previous_expiry_date")
	private LocalDate previousExpiryDate;
	@JsonProperty("new_expiry_date")
	private LocalDate newExpiryDate;
	@JsonProperty("changed_by")
	private String changedBy;
	private LocalDateTime changedAt;
}
