package jp.xoki.web.zunda.smarthomewebserverbackend.item.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jp.xoki.web.zunda.smarthomewebserverbackend.item.model.InventoryStockChangeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jp.xoki.web.zunda.smarthomewebserverbackend.item.constants.ItemMessageConstants;

/** 実在庫の数量操作に利用するリクエストDTO。 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemStockOperationRequest {

	/** 操作対象の実在庫ID。 */
	@NotNull(message = ItemMessageConstants.ITEM_STOCK_OPERATION_INVENTORY_STOCK_ID_REQUIRED)
	@JsonProperty("inventory_stock_id")
	private Long inventoryStockId;

	/** 入庫、出庫、棚卸、破棄、削除を表す操作種別。 */
	@NotNull(message = ItemMessageConstants.ITEM_STOCK_OPERATION_CHANGE_TYPE_REQUIRED)
	@JsonProperty("change_type")
	private InventoryStockChangeType changeType;

	/** 操作数量。削除操作では履歴上の入力値として扱う。 */
	@NotNull(message = ItemMessageConstants.ITEM_STOCK_OPERATION_QUANTITY_REQUIRED)
	@Min(value = 0, message = ItemMessageConstants.ITEM_STOCK_OPERATION_QUANTITY_MIN)
	private Integer quantity;

	/** 操作理由。 */
	@Size(max = 255, message = ItemMessageConstants.ITEM_STOCK_OPERATION_REASON_SIZE)
	private String reason;

	/** 操作に関する補足メモ。 */
	private String memo;
}
