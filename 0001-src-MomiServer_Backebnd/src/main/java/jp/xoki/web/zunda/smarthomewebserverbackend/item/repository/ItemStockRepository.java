package jp.xoki.web.zunda.smarthomewebserverbackend.item.repository;

import jp.xoki.web.zunda.jooq.tables.MInventoryItems;
import jp.xoki.web.zunda.jooq.tables.TInventoryStockHistories;
import jp.xoki.web.zunda.jooq.tables.TInventoryStocks;
import jp.xoki.web.zunda.jooq.tables.records.TInventoryStockHistoriesRecord;
import jp.xoki.web.zunda.jooq.tables.records.TInventoryStocksRecord;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.time.Clock;

/** 実在庫と在庫操作履歴へアクセスするRepository。 */
@Repository
@RequiredArgsConstructor
public class ItemStockRepository {

	/** jOOQ実行コンテキスト。 */
	private final DSLContext dsl;

	private final Clock clock;

	/**
	 * 実在庫をID昇順で全件取得する。
	 *
	 * @return 実在庫レコード一覧
	 */
	public List<TInventoryStocksRecord> findAll() {
		return dsl.selectFrom(TInventoryStocks.T_INVENTORY_STOCKS)
				.orderBy(TInventoryStocks.T_INVENTORY_STOCKS.ID.asc())
				.fetchInto(TInventoryStocksRecord.class);
	}

	/**
	 * 実在庫IDに一致する実在庫を取得する。
	 *
	 * @param id 実在庫ID
	 * @return 実在庫レコード
	 */
	public Optional<TInventoryStocksRecord> findById(Long id) {
		return dsl.selectFrom(TInventoryStocks.T_INVENTORY_STOCKS)
				.where(TInventoryStocks.T_INVENTORY_STOCKS.ID.eq(id))
				.fetchOptionalInto(TInventoryStocksRecord.class);
	}

	/**
	 * 数量操作向けに対象実在庫を行ロックして取得する。
	 *
	 * @param id 実在庫ID
	 * @return ロック取得済みの実在庫レコード
	 */
	public Optional<TInventoryStocksRecord> findByIdForUpdate(Long id) {
		return dsl.selectFrom(TInventoryStocks.T_INVENTORY_STOCKS)
				.where(TInventoryStocks.T_INVENTORY_STOCKS.ID.eq(id))
				.forUpdate()
				.fetchOptionalInto(TInventoryStocksRecord.class);
	}

	/**
	 * 品目マスタに設定された最低在庫数を取得する。
	 *
	 * @param inventoryItemId 在庫品目ID
	 * @return 最低在庫数
	 */
	public Integer findMinimumQuantityByItemId(Long inventoryItemId) {
		return dsl.select(MInventoryItems.M_INVENTORY_ITEMS.MIN_QUANTITY)
				.from(MInventoryItems.M_INVENTORY_ITEMS)
				.where(MInventoryItems.M_INVENTORY_ITEMS.ID.eq(inventoryItemId))
				.fetchOne(MInventoryItems.M_INVENTORY_ITEMS.MIN_QUANTITY);
	}

	/**
	 * 実在庫を新規登録する。
	 *
	 * @param record 登録する実在庫レコード
	 * @return 登録された実在庫ID
	 */
	public Long save(TInventoryStocksRecord databaseRecord) {
		LocalDateTime now = LocalDateTime.now(clock);

		return dsl.insertInto(TInventoryStocks.T_INVENTORY_STOCKS)
				.set(TInventoryStocks.T_INVENTORY_STOCKS.INVENTORY_ITEM_ID, databaseRecord.getInventoryItemId())
				.set(TInventoryStocks.T_INVENTORY_STOCKS.STORAGE_LOCATION, databaseRecord.getStorageLocation())
				.set(TInventoryStocks.T_INVENTORY_STOCKS.QUANTITY, databaseRecord.getQuantity())
				.set(TInventoryStocks.T_INVENTORY_STOCKS.REPLENISH_QUANTITY, databaseRecord.getReplenishQuantity())
				.set(TInventoryStocks.T_INVENTORY_STOCKS.INVENTORY_STATUS, databaseRecord.getInventoryStatus())
				.set(TInventoryStocks.T_INVENTORY_STOCKS.EXPIRATION_DATE, databaseRecord.getExpirationDate())
				.set(TInventoryStocks.T_INVENTORY_STOCKS.MEMO, databaseRecord.getMemo())
				.set(TInventoryStocks.T_INVENTORY_STOCKS.CREATED_DT, now)
				.set(TInventoryStocks.T_INVENTORY_STOCKS.UPDATED_DT, now)
				.returningResult(TInventoryStocks.T_INVENTORY_STOCKS.ID)
				.fetchOne(TInventoryStocks.T_INVENTORY_STOCKS.ID);
	}

	/**
	 * 実在庫の基本情報を更新する。
	 *
	 * @param record 更新する実在庫レコード
	 * @return 更新件数
	 */
	public int update(TInventoryStocksRecord databaseRecord) {
		return dsl.update(TInventoryStocks.T_INVENTORY_STOCKS)
				.set(TInventoryStocks.T_INVENTORY_STOCKS.INVENTORY_ITEM_ID, databaseRecord.getInventoryItemId())
				.set(TInventoryStocks.T_INVENTORY_STOCKS.STORAGE_LOCATION, databaseRecord.getStorageLocation())
				.set(TInventoryStocks.T_INVENTORY_STOCKS.QUANTITY, databaseRecord.getQuantity())
				.set(TInventoryStocks.T_INVENTORY_STOCKS.REPLENISH_QUANTITY, databaseRecord.getReplenishQuantity())
				.set(TInventoryStocks.T_INVENTORY_STOCKS.INVENTORY_STATUS, databaseRecord.getInventoryStatus())
				.set(TInventoryStocks.T_INVENTORY_STOCKS.EXPIRATION_DATE, databaseRecord.getExpirationDate())
				.set(TInventoryStocks.T_INVENTORY_STOCKS.MEMO, databaseRecord.getMemo())
				.set(TInventoryStocks.T_INVENTORY_STOCKS.UPDATED_DT, LocalDateTime.now(clock))
				.where(TInventoryStocks.T_INVENTORY_STOCKS.ID.eq(databaseRecord.getId()))
				.execute();
	}

	/**
	 * 数量操作結果として実在庫の数量と状態のみを更新する。
	 *
	 * @param id 実在庫ID
	 * @param quantity 更新後数量
	 * @param inventoryStatus 更新後在庫状態
	 * @return 更新件数
	 */
	public int updateQuantityAndStatus(Long id, Integer quantity, String inventoryStatus) {
		return dsl.update(TInventoryStocks.T_INVENTORY_STOCKS)
				.set(TInventoryStocks.T_INVENTORY_STOCKS.QUANTITY, quantity)
				.set(TInventoryStocks.T_INVENTORY_STOCKS.INVENTORY_STATUS, inventoryStatus)
				.set(TInventoryStocks.T_INVENTORY_STOCKS.UPDATED_DT, LocalDateTime.now(clock))
				.where(TInventoryStocks.T_INVENTORY_STOCKS.ID.eq(id))
				.execute();
	}

	/**
	 * 実在庫の数量変更履歴を登録する。
	 *
	 * @param record 登録する数量変更履歴
	 * @return 登録された履歴ID
	 */
	public Long saveHistory(TInventoryStockHistoriesRecord databaseRecord) {
		LocalDateTime now = LocalDateTime.now(clock);

		return dsl.insertInto(TInventoryStockHistories.T_INVENTORY_STOCK_HISTORIES)
				.set(TInventoryStockHistories.T_INVENTORY_STOCK_HISTORIES.INVENTORY_STOCK_ID, databaseRecord.getInventoryStockId())
				.set(TInventoryStockHistories.T_INVENTORY_STOCK_HISTORIES.CHANGE_TYPE, databaseRecord.getChangeType())
				.set(TInventoryStockHistories.T_INVENTORY_STOCK_HISTORIES.QUANTITY_DELTA, databaseRecord.getQuantityDelta())
				.set(TInventoryStockHistories.T_INVENTORY_STOCK_HISTORIES.QUANTITY_BEFORE, databaseRecord.getQuantityBefore())
				.set(TInventoryStockHistories.T_INVENTORY_STOCK_HISTORIES.QUANTITY_AFTER, databaseRecord.getQuantityAfter())
				.set(TInventoryStockHistories.T_INVENTORY_STOCK_HISTORIES.INVENTORY_STATUS_BEFORE, databaseRecord.getInventoryStatusBefore())
				.set(TInventoryStockHistories.T_INVENTORY_STOCK_HISTORIES.INVENTORY_STATUS_AFTER, databaseRecord.getInventoryStatusAfter())
				.set(TInventoryStockHistories.T_INVENTORY_STOCK_HISTORIES.REASON, databaseRecord.getReason())
				.set(TInventoryStockHistories.T_INVENTORY_STOCK_HISTORIES.MEMO, databaseRecord.getMemo())
				.set(TInventoryStockHistories.T_INVENTORY_STOCK_HISTORIES.CHANGED_DT, now)
				.set(TInventoryStockHistories.T_INVENTORY_STOCK_HISTORIES.CREATED_DT, now)
				.returningResult(TInventoryStockHistories.T_INVENTORY_STOCK_HISTORIES.ID)
				.fetchOne(TInventoryStockHistories.T_INVENTORY_STOCK_HISTORIES.ID);
	}

	/**
	 * 実在庫IDに紐づく数量変更履歴を新しい順に取得する。
	 *
	 * @param inventoryStockId 実在庫ID
	 * @return 数量変更履歴一覧
	 */
	public List<TInventoryStockHistoriesRecord> findHistoriesByStockId(Long inventoryStockId) {
		return dsl.selectFrom(TInventoryStockHistories.T_INVENTORY_STOCK_HISTORIES)
				.where(TInventoryStockHistories.T_INVENTORY_STOCK_HISTORIES.INVENTORY_STOCK_ID.eq(inventoryStockId))
				.orderBy(TInventoryStockHistories.T_INVENTORY_STOCK_HISTORIES.CHANGED_DT.desc(),
						TInventoryStockHistories.T_INVENTORY_STOCK_HISTORIES.ID.desc())
				.fetchInto(TInventoryStockHistoriesRecord.class);
	}
}
