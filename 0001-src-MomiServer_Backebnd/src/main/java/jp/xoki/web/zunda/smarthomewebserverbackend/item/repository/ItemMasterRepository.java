package jp.xoki.web.zunda.smarthomewebserverbackend.item.repository;

import jp.xoki.web.zunda.jooq.tables.MInventoryItems;
import jp.xoki.web.zunda.jooq.tables.records.MInventoryItemsRecord;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.time.Clock;

/** 在庫品目マスタへアクセスするRepository。 */
@Repository
@RequiredArgsConstructor
public class ItemMasterRepository {

	/** jOOQ実行コンテキスト。 */
	private final DSLContext dsl;

	private final Clock clock;

	/**
	 * 在庫品目マスタをID昇順で全件取得する。
	 *
	 * @return 在庫品目マスタ一覧
	 */
	public List<MInventoryItemsRecord> findAll() {
		return dsl.selectFrom(MInventoryItems.M_INVENTORY_ITEMS)
				.orderBy(MInventoryItems.M_INVENTORY_ITEMS.ID.asc())
				.fetchInto(MInventoryItemsRecord.class);
	}

	/**
	 * 在庫品目IDに一致する在庫品目マスタを取得する。
	 *
	 * @param id 在庫品目ID
	 * @return 在庫品目マスタ
	 */
	public Optional<MInventoryItemsRecord> findById(Long id) {
		return dsl.selectFrom(MInventoryItems.M_INVENTORY_ITEMS)
				.where(MInventoryItems.M_INVENTORY_ITEMS.ID.eq(id))
				.fetchOptionalInto(MInventoryItemsRecord.class);
	}

	/**
	 * 在庫品目マスタを新規登録する。
	 *
	 * @param record 登録する在庫品目マスタ
	 * @return 登録された在庫品目ID
	 */
	public Long save(MInventoryItemsRecord databaseRecord) {
		LocalDateTime now = LocalDateTime.now(clock);

		return dsl.insertInto(MInventoryItems.M_INVENTORY_ITEMS)
				.set(MInventoryItems.M_INVENTORY_ITEMS.ITEM_NAME, databaseRecord.getItemName())
				.set(MInventoryItems.M_INVENTORY_ITEMS.CATEGORY, databaseRecord.getCategory())
				.set(MInventoryItems.M_INVENTORY_ITEMS.UNIT, databaseRecord.getUnit())
				.set(MInventoryItems.M_INVENTORY_ITEMS.MIN_QUANTITY, databaseRecord.getMinQuantity())
				.set(MInventoryItems.M_INVENTORY_ITEMS.MANUFACTURER, databaseRecord.getManufacturer())
				.set(MInventoryItems.M_INVENTORY_ITEMS.MODEL_NUMBER, databaseRecord.getModelNumber())
				.set(MInventoryItems.M_INVENTORY_ITEMS.JAN_CODE, databaseRecord.getJanCode())
				.set(MInventoryItems.M_INVENTORY_ITEMS.PURCHASE_URL, databaseRecord.getPurchaseUrl())
				.set(MInventoryItems.M_INVENTORY_ITEMS.STATUS, databaseRecord.getStatus())
				.set(MInventoryItems.M_INVENTORY_ITEMS.MEMO, databaseRecord.getMemo())
				.set(MInventoryItems.M_INVENTORY_ITEMS.CREATED_DT, now)
				.set(MInventoryItems.M_INVENTORY_ITEMS.UPDATED_DT, now)
				.returningResult(MInventoryItems.M_INVENTORY_ITEMS.ID)
				.fetchOne(MInventoryItems.M_INVENTORY_ITEMS.ID);
	}

	/**
	 * 在庫品目マスタを更新する。
	 *
	 * @param record 更新する在庫品目マスタ
	 * @return 更新件数
	 */
	public int update(MInventoryItemsRecord databaseRecord) {
		return dsl.update(MInventoryItems.M_INVENTORY_ITEMS)
				.set(MInventoryItems.M_INVENTORY_ITEMS.ITEM_NAME, databaseRecord.getItemName())
				.set(MInventoryItems.M_INVENTORY_ITEMS.CATEGORY, databaseRecord.getCategory())
				.set(MInventoryItems.M_INVENTORY_ITEMS.UNIT, databaseRecord.getUnit())
				.set(MInventoryItems.M_INVENTORY_ITEMS.MIN_QUANTITY, databaseRecord.getMinQuantity())
				.set(MInventoryItems.M_INVENTORY_ITEMS.MANUFACTURER, databaseRecord.getManufacturer())
				.set(MInventoryItems.M_INVENTORY_ITEMS.MODEL_NUMBER, databaseRecord.getModelNumber())
				.set(MInventoryItems.M_INVENTORY_ITEMS.JAN_CODE, databaseRecord.getJanCode())
				.set(MInventoryItems.M_INVENTORY_ITEMS.PURCHASE_URL, databaseRecord.getPurchaseUrl())
				.set(MInventoryItems.M_INVENTORY_ITEMS.STATUS, databaseRecord.getStatus())
				.set(MInventoryItems.M_INVENTORY_ITEMS.MEMO, databaseRecord.getMemo())
				.set(MInventoryItems.M_INVENTORY_ITEMS.UPDATED_DT, LocalDateTime.now(clock))
				.where(MInventoryItems.M_INVENTORY_ITEMS.ID.eq(databaseRecord.getId()))
				.execute();
	}
}
