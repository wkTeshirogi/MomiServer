package jp.xoki.web.zunda.smarthomewebserverbackend.batch.repository;

import jp.xoki.web.zunda.jooq.tables.TBatchExecutionHistories;
import jp.xoki.web.zunda.jooq.tables.records.TBatchExecutionHistoriesRecord;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.time.Clock;

/** バッチ実行履歴へアクセスするRepository。 */
@Repository
public class BatchExecutionHistoryRepository {
	private final DSLContext dsl;

	private final Clock clock;

	/**
	 * Repositoryを生成する。
	 *
	 * @param dsl jOOQ DSLContext
	 * @param clock 共通の業務時刻
	 */
	public BatchExecutionHistoryRepository(DSLContext dsl, Clock clock) {
		this.dsl = dsl;
		this.clock = clock;
	}

	/**
	 * バッチ定義IDに紐づく実行履歴を開始日時降順、ID降順で取得する。
	 *
	 * @param batchDefinitionId バッチ定義ID
	 * @return バッチ実行履歴レコード一覧
	 */
	public List<TBatchExecutionHistoriesRecord> findByBatchDefinitionId(Long batchDefinitionId) {
		return dsl.selectFrom(TBatchExecutionHistories.T_BATCH_EXECUTION_HISTORIES)
				.where(TBatchExecutionHistories.T_BATCH_EXECUTION_HISTORIES.BATCH_DEFINITION_ID.eq(batchDefinitionId))
				.orderBy(TBatchExecutionHistories.T_BATCH_EXECUTION_HISTORIES.EXECUTION_STARTED_DT.desc(),
						TBatchExecutionHistories.T_BATCH_EXECUTION_HISTORIES.ID.desc())
				.fetchInto(TBatchExecutionHistoriesRecord.class);
	}

	/**
	 * IDに一致するバッチ実行履歴を取得する。
	 *
	 * @param id バッチ実行履歴ID
	 * @return バッチ実行履歴レコード
	 */
	public Optional<TBatchExecutionHistoriesRecord> findById(Long id) {
		return dsl.selectFrom(TBatchExecutionHistories.T_BATCH_EXECUTION_HISTORIES)
				.where(TBatchExecutionHistories.T_BATCH_EXECUTION_HISTORIES.ID.eq(id))
				.fetchOptionalInto(TBatchExecutionHistoriesRecord.class);
	}

	/**
	 * バッチ実行履歴を登録する。
	 *
	 * @param record 登録するバッチ実行履歴
	 * @return 登録されたバッチ実行履歴ID
	 */
	public Long save(TBatchExecutionHistoriesRecord databaseRecord) {
		LocalDateTime now = LocalDateTime.now(clock);
		databaseRecord.setCreatedDt(now);
		databaseRecord.setUpdatedDt(now);

		return dsl.insertInto(TBatchExecutionHistories.T_BATCH_EXECUTION_HISTORIES)
				.set(databaseRecord)
				.set(TBatchExecutionHistories.T_BATCH_EXECUTION_HISTORIES.ID,
						DSL.defaultValue(TBatchExecutionHistories.T_BATCH_EXECUTION_HISTORIES.ID))
				.returningResult(TBatchExecutionHistories.T_BATCH_EXECUTION_HISTORIES.ID)
				.fetchOne(TBatchExecutionHistories.T_BATCH_EXECUTION_HISTORIES.ID);
	}

	/**
	 * バッチ実行履歴を更新する。
	 *
	 * @param record 更新するバッチ実行履歴
	 * @return 更新件数
	 */
	public int update(TBatchExecutionHistoriesRecord databaseRecord) {
		databaseRecord.setUpdatedDt(LocalDateTime.now(clock));

		return dsl.update(TBatchExecutionHistories.T_BATCH_EXECUTION_HISTORIES)
				.set(databaseRecord)
				.where(TBatchExecutionHistories.T_BATCH_EXECUTION_HISTORIES.ID.eq(databaseRecord.getId()))
				.execute();
	}
}
