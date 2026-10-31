package jp.xoki.web.zunda.smarthomewebserverbackend.batch.repository;

import static jp.xoki.web.zunda.smarthomewebserverbackend.batch.constants.BatchDefinitionConstants.ACTIVE_STATUS;
import static jp.xoki.web.zunda.jooq.Tables.T_BATCH_EXECUTION_REQUESTS;

import jp.xoki.web.zunda.jooq.tables.MBatchDefinitions;
import jp.xoki.web.zunda.jooq.tables.records.MBatchDefinitionsRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Clock;

/** バッチ定義マスタへアクセスするRepository。 */
@Repository
public class BatchDefinitionRepository {
	private final DSLContext dsl;

	private final Clock clock;

	/**
	 * Repositoryを生成する。
	 *
	 * @param dsl jOOQ DSLContext
	 * @param clock 共通の業務時刻
	 */
	public BatchDefinitionRepository(DSLContext dsl, Clock clock) {
		this.dsl = dsl;
		this.clock = clock;
	}

	/**
	 * すべてのバッチ定義をID昇順で取得する。
	 *
	 * @return バッチ定義レコード一覧
	 */
	public List<MBatchDefinitionsRecord> findAll() {
		return dsl.selectFrom(MBatchDefinitions.M_BATCH_DEFINITIONS)
				.where(MBatchDefinitions.M_BATCH_DEFINITIONS.STATUS.eq(ACTIVE_STATUS))
				.orderBy(MBatchDefinitions.M_BATCH_DEFINITIONS.ID.asc())
				.fetchInto(MBatchDefinitionsRecord.class);
	}

	/**
	 * 有効なバッチ定義を優先度、ID昇順で取得する。
	 *
	 * @return 有効なバッチ定義レコード一覧
	 */
	public List<MBatchDefinitionsRecord> findEnabled() {
		return dsl.selectFrom(MBatchDefinitions.M_BATCH_DEFINITIONS)
				.where(MBatchDefinitions.M_BATCH_DEFINITIONS.ENABLED.isTrue())
				.and(MBatchDefinitions.M_BATCH_DEFINITIONS.STATUS.eq(ACTIVE_STATUS))
				.orderBy(MBatchDefinitions.M_BATCH_DEFINITIONS.PRIORITY.asc(),
						MBatchDefinitions.M_BATCH_DEFINITIONS.ID.asc())
				.fetchInto(MBatchDefinitionsRecord.class);
	}

	/**
	 * IDに一致するバッチ定義を取得する。
	 *
	 * @param id バッチ定義ID
	 * @return バッチ定義レコード
	 */
	public Optional<MBatchDefinitionsRecord> findById(Long id) {
		return dsl.selectFrom(MBatchDefinitions.M_BATCH_DEFINITIONS)
				.where(MBatchDefinitions.M_BATCH_DEFINITIONS.ID.eq(id))
				.fetchOptionalInto(MBatchDefinitionsRecord.class);
	}

	/**
	 * 外部参照用実行IDに紐づくバッチ定義の要求ロールを取得する。
	 *
	 * @param executionId 外部参照用実行ID
	 * @return バッチ定義に設定された要求ロール
	 */
	public Optional<String> findRequiredRoleByExecutionId(UUID executionId) {

		return dsl.select(MBatchDefinitions.M_BATCH_DEFINITIONS.REQUIRED_ROLE)
				.from(MBatchDefinitions.M_BATCH_DEFINITIONS)
				.innerJoin(T_BATCH_EXECUTION_REQUESTS)
				.on(MBatchDefinitions.M_BATCH_DEFINITIONS.ID.eq(T_BATCH_EXECUTION_REQUESTS.BATCH_DEFINITION_ID))
				.where(T_BATCH_EXECUTION_REQUESTS.EXECUTION_ID.eq(executionId))
				.fetchOptional(MBatchDefinitions.M_BATCH_DEFINITIONS.REQUIRED_ROLE);
	}

	/**
	 * IDに一致するバッチ定義をロックして取得する。
	 *
	 * @param id バッチ定義ID
	 * @return ロックしたバッチ定義レコード
	 */
	public Optional<MBatchDefinitionsRecord> lockById(Long id) {
		return dsl.selectFrom(MBatchDefinitions.M_BATCH_DEFINITIONS)
				.where(MBatchDefinitions.M_BATCH_DEFINITIONS.ID.eq(id))
				.forUpdate()
				.fetchOptionalInto(MBatchDefinitionsRecord.class);
	}

	/**
	 * バッチ名に一致するバッチ定義を取得する。
	 *
	 * @param batchName バッチ名
	 * @return バッチ定義レコード
	 */
	public Optional<MBatchDefinitionsRecord> findByBatchName(String batchName) {
		return dsl.selectFrom(MBatchDefinitions.M_BATCH_DEFINITIONS)
				.where(MBatchDefinitions.M_BATCH_DEFINITIONS.BATCH_NAME.eq(batchName))
				.fetchOptionalInto(MBatchDefinitionsRecord.class);
	}

	/**
	 * バッチ定義を登録する。
	 *
	 * @param record 登録するバッチ定義
	 * @return 登録されたバッチ定義ID
	 */
	public Long save(MBatchDefinitionsRecord databaseRecord) {
		LocalDateTime now = LocalDateTime.now(clock);
		databaseRecord.setVersion(1L);
		databaseRecord.setStatus(ACTIVE_STATUS);
		databaseRecord.setCreatedDt(now);
		databaseRecord.setUpdatedDt(now);

		return dsl.insertInto(MBatchDefinitions.M_BATCH_DEFINITIONS)
				.set(databaseRecord)
				.returningResult(MBatchDefinitions.M_BATCH_DEFINITIONS.ID)
				.fetchOne(MBatchDefinitions.M_BATCH_DEFINITIONS.ID);
	}

	/**
	 * バッチ定義を更新する。
	 *
	 * @param record 更新するバッチ定義
	 * @param expectedVersion 更新前の更新番号
	 * @return 更新件数
	 */
	public int update(MBatchDefinitionsRecord databaseRecord, long expectedVersion) {
		databaseRecord.setUpdatedDt(LocalDateTime.now(clock));
		databaseRecord.setVersion(expectedVersion + 1);

		return dsl.update(MBatchDefinitions.M_BATCH_DEFINITIONS)
				.set(databaseRecord)
				.where(MBatchDefinitions.M_BATCH_DEFINITIONS.ID.eq(databaseRecord.getId()))
				.and(MBatchDefinitions.M_BATCH_DEFINITIONS.VERSION.eq(expectedVersion))
				.and(MBatchDefinitions.M_BATCH_DEFINITIONS.STATUS.eq(ACTIVE_STATUS))
				.execute();
	}

	/**
	 * 指定した定義に未完了の実行要求が存在するか確認する。
	 *
	 * @param batchDefinitionId バッチ定義ID
	 * @return 未完了要求が存在する場合true
	 */
	public boolean hasNonTerminalExecutionRequests(Long batchDefinitionId) {
		return dsl.fetchExists(dsl.selectOne()
				.from(T_BATCH_EXECUTION_REQUESTS)
				.where(T_BATCH_EXECUTION_REQUESTS.BATCH_DEFINITION_ID.eq(batchDefinitionId))
				.and(T_BATCH_EXECUTION_REQUESTS.EXECUTION_STATUS
						.in("queued", "dispatched", "running")));
	}
}
