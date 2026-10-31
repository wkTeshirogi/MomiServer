package jp.xoki.web.zunda.smarthomewebserverbackend.external.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** 操作IDで再送時の二重反映を防ぐ数量操作入力。
 * @param operationId 操作ID
 * @param changeType 操作種別
 * @param quantity 操作数量
 * @param reason 操作理由
 */
public record ExternalStockOperationRequest(@NotNull UUID operationId,
		@NotBlank @Pattern(regexp = "inbound|outbound|stocktake") String changeType,
		@NotNull @Min(0) Integer quantity, @Size(max = 255) String reason) {
}
