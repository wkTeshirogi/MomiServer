package jp.xoki.web.zunda.smarthomewebserverbackend.external.constants;

import java.util.Set;

import static jp.xoki.web.zunda.smarthomewebserverbackend.item.model.InventoryStockChangeType.INBOUND;
import static jp.xoki.web.zunda.smarthomewebserverbackend.item.model.InventoryStockChangeType.OUTBOUND;
import static jp.xoki.web.zunda.smarthomewebserverbackend.item.model.InventoryStockChangeType.STOCKTAKE;

/** ExternalInventoryServiceで使用する固定値。カテゴリ内の利用元から参照する。 */
public final class ExternalInventoryServiceConstants {

	public static final int MAX_LIMIT = 100;

	public static final Set<String> OPERATIONS = Set.of(
			INBOUND.getStoredName(), OUTBOUND.getStoredName(), STOCKTAKE.getStoredName());

	private ExternalInventoryServiceConstants() {
	}
}
