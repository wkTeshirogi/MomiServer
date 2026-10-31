package jp.xoki.web.zunda.smarthomewebserverbackend.item.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jp.xoki.web.zunda.smarthomewebserverbackend.item.model.InventoryStatus;
import jp.xoki.web.zunda.smarthomewebserverbackend.item.model.InventoryStockChangeType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/** 実在庫の数量変更履歴を返却するDTO。 */
@Getter
@Builder
public class ItemStockHistoryDetailDto {

	/** 数量変更履歴ID。 */
	private Long id;

	/** 操作対象の実在庫ID。 */
	@JsonProperty("inventory_stock_id")
	private Long inventoryStockId;

	/** 在庫操作種別。 */
	@JsonProperty("change_type")
	private InventoryStockChangeType changeType;

	/** 操作前後の数量差分。 */
	@JsonProperty("quantity_delta")
	private Integer quantityDelta;

	/** 操作前数量。 */
	@JsonProperty("quantity_before")
	private Integer quantityBefore;

	/** 操作後数量。 */
	@JsonProperty("quantity_after")
	private Integer quantityAfter;

	/** 操作前在庫状態。 */
	@JsonProperty("inventory_status_before")
	private InventoryStatus inventoryStatusBefore;

	/** 操作後在庫状態。 */
	@JsonProperty("inventory_status_after")
	private InventoryStatus inventoryStatusAfter;

	/** 操作理由。 */
	private String reason;

	/** 補足メモ。 */
	private String memo;

	/** 操作日時。 */
	private LocalDateTime changedAt;

	/** 履歴作成日時。 */
	private LocalDateTime createdAt;
}
