package jp.xoki.web.zunda.smarthomewebserverbackend.external.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jp.xoki.web.zunda.smarthomewebserverbackend.external.dto.ExternalStockDto;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.Field;
import java.util.Objects;
import org.jooq.CommonTableExpression;
import org.jooq.SelectConditionStep;
import org.jooq.JSONB;
import jp.xoki.web.zunda.jooq.tables.records.TInventoryStocksRecord;
import org.springframework.stereotype.Repository;

import static jp.xoki.web.zunda.jooq.Tables.T_INVENTORY_STOCKS;
import static jp.xoki.web.zunda.jooq.Tables.M_INVENTORY_ITEMS;
import static jp.xoki.web.zunda.jooq.Tables.T_EXTERNAL_INVENTORY_OPERATIONS;
import static org.jooq.impl.DSL.name;

/** 外部公開在庫の限定射影と数量操作の重複防止記録。 */
@Repository
@RequiredArgsConstructor
public class ExternalInventoryRepository {
	private final DSLContext dsl;

	/** 在庫一覧取得。
	 * @param limit 取得件数
	 * @param offset 開始位置
	 * @return 在庫一覧
	 */
	public List<ExternalStockDto> list(int limit, int offset) {
		return stockQuery(T_INVENTORY_STOCKS).orderBy(T_INVENTORY_STOCKS.ID).limit(limit).offset(offset).fetch(this::map);
	}

	/** 在庫詳細取得。
	 * @param id 実在庫ID
	 * @return 在庫詳細
	 */
	public Optional<ExternalStockDto> find(long id) {
		return stockQuery(T_INVENTORY_STOCKS).and(T_INVENTORY_STOCKS.ID.eq(id)).fetchOptional(this::map);
	}

	/** 更新対象の行ロック。
	 * @param id 実在庫ID
	 * @return 外部更新可能な在庫
	 */
	public Optional<ExternalStockDto> lock(long id) {
		// jOOQの単一SELECTでは混在ロックを指定できないため、在庫行をCTE内で先に排他ロックする。
        // 外側では品目行だけを共有ロックし、別在庫の読取・数量操作を不必要に排他しない。
        CommonTableExpression<TInventoryStocksRecord> stock = name("locked_stock").asMaterialized(
                dsl.selectFrom(T_INVENTORY_STOCKS).where(T_INVENTORY_STOCKS.ID.eq(id))
                        .and(T_INVENTORY_STOCKS.INVENTORY_STATUS.ne("deleted"))
                        .andExists(dsl.selectOne().from(M_INVENTORY_ITEMS)
                                .where(M_INVENTORY_ITEMS.ID.eq(T_INVENTORY_STOCKS.INVENTORY_ITEM_ID))
                                .and(M_INVENTORY_ITEMS.STATUS.eq("active"))).forUpdate());
        return stockQuery(stock, dsl.with(stock)).forShare().of(M_INVENTORY_ITEMS).fetchOptional(this::map);
	}

	/** 操作IDの予約。
	 * @param keyId キーID
	 * @param operationId 操作ID
	 * @param hash 正規化入力のハッシュ
	 * @return 初回の場合true
	 */
	public boolean reserve(UUID keyId, UUID operationId, String hash) {
		return dsl.insertInto(T_EXTERNAL_INVENTORY_OPERATIONS).set(T_EXTERNAL_INVENTORY_OPERATIONS.API_KEY_ID, keyId)
                .set(T_EXTERNAL_INVENTORY_OPERATIONS.OPERATION_ID, operationId)
                .set(T_EXTERNAL_INVENTORY_OPERATIONS.REQUEST_HASH, hash).onConflictDoNothing().execute() == 1;
	}

	/** 前回結果を取得する。
	 * @param keyId キーID
	 * @param operationId 操作ID
	 * @return 操作記録
	 */
	public SavedOperation previous(UUID keyId, UUID operationId) {
		return dsl.selectFrom(T_EXTERNAL_INVENTORY_OPERATIONS)
                .where(T_EXTERNAL_INVENTORY_OPERATIONS.API_KEY_ID.eq(keyId))
                .and(T_EXTERNAL_INVENTORY_OPERATIONS.OPERATION_ID.eq(operationId)).forUpdate()
                .fetchSingle(row -> new SavedOperation(row.getRequestHash(),
                        row.getResponse() == null ? null : row.getResponse().data()));
	}

	/** 操作結果保存。
	 * @param keyId キーID
	 * @param operationId 操作ID
	 * @param response 初回応答JSON
	 */
	public void complete(UUID keyId, UUID operationId, String response) {
		dsl.update(T_EXTERNAL_INVENTORY_OPERATIONS).set(T_EXTERNAL_INVENTORY_OPERATIONS.RESPONSE, JSONB.valueOf(response))
                .where(T_EXTERNAL_INVENTORY_OPERATIONS.API_KEY_ID.eq(keyId))
                .and(T_EXTERNAL_INVENTORY_OPERATIONS.OPERATION_ID.eq(operationId)).execute();
	}

	private SelectConditionStep<Record> stockQuery(Table<?> stock) {
        return stockQuery(stock, dsl.with());
    }

    private SelectConditionStep<Record> stockQuery(Table<?> stock, org.jooq.WithStep query) {
        return query.select(List.of(projectedField(stock, T_INVENTORY_STOCKS.ID), projectedField(stock, T_INVENTORY_STOCKS.INVENTORY_ITEM_ID),
                        M_INVENTORY_ITEMS.ITEM_NAME, M_INVENTORY_ITEMS.MANUFACTURER, M_INVENTORY_ITEMS.MODEL_NUMBER,
                        M_INVENTORY_ITEMS.CATEGORY, projectedField(stock, T_INVENTORY_STOCKS.STORAGE_LOCATION),
                        projectedField(stock, T_INVENTORY_STOCKS.QUANTITY), M_INVENTORY_ITEMS.MIN_QUANTITY, M_INVENTORY_ITEMS.UNIT,
                        projectedField(stock, T_INVENTORY_STOCKS.INVENTORY_STATUS)))
                .from(stock).innerJoin(M_INVENTORY_ITEMS).on(M_INVENTORY_ITEMS.ID.eq(projectedField(stock, T_INVENTORY_STOCKS.INVENTORY_ITEM_ID)))
                .where(projectedField(stock, T_INVENTORY_STOCKS.INVENTORY_STATUS).ne("deleted")).and(M_INVENTORY_ITEMS.STATUS.eq("active"));
    }

    private <T> Field<T> projectedField(Table<?> stock, Field<T> column) {
        // CTEの列を生成定義と照合し、欠けた射影をクエリ実行前に検出する。
        return Objects.requireNonNull(stock.field(column), () -> "Missing stock projection: " + column.getName());
    }

    private ExternalStockDto map(Record result) {
        return new ExternalStockDto(result.get(T_INVENTORY_STOCKS.ID), result.get(T_INVENTORY_STOCKS.INVENTORY_ITEM_ID),
                result.get(M_INVENTORY_ITEMS.ITEM_NAME), result.get(M_INVENTORY_ITEMS.MANUFACTURER),
                result.get(M_INVENTORY_ITEMS.MODEL_NUMBER), result.get(M_INVENTORY_ITEMS.CATEGORY),
                result.get(T_INVENTORY_STOCKS.STORAGE_LOCATION), result.get(T_INVENTORY_STOCKS.QUANTITY),
                result.get(M_INVENTORY_ITEMS.MIN_QUANTITY), result.get(M_INVENTORY_ITEMS.UNIT),
                result.get(T_INVENTORY_STOCKS.INVENTORY_STATUS));
    }

	/** 入力ハッシュと初回応答。
	 * @param requestHash 入力ハッシュ
	 * @param response 応答JSON
	 */
	public record SavedOperation(String requestHash, String response) {
	}
}
