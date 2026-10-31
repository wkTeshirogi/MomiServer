package jp.xoki.web.zunda.smarthomewebserverbackend.batch.repository;

import jp.xoki.web.zunda.jooq.tables.MBatchSchedules;
import jp.xoki.web.zunda.jooq.tables.records.MBatchSchedulesRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.time.Clock;

/** バッチスケジュールマスタへアクセスするRepository。 */
@Repository
public class BatchScheduleRepository {
	private final DSLContext dsl;

	private final Clock clock;

	/**
	 * Repositoryを生成する。
	 *
	 * @param dsl jOOQ DSLContext
	 * @param clock 共通の業務時刻
	 */
	public BatchScheduleRepository(DSLContext dsl, Clock clock) {
		this.dsl = dsl;
		this.clock = clock;
	}

	/**
	 * すべてのバッチスケジュールを優先度、ID昇順で取得する。
	 *
	 * @return バッチスケジュールレコード一覧
	 */
	public List<MBatchSchedulesRecord> findAll() {
		return dsl.selectFrom(MBatchSchedules.M_BATCH_SCHEDULES)
				.orderBy(MBatchSchedules.M_BATCH_SCHEDULES.PRIORITY.asc(),
						MBatchSchedules.M_BATCH_SCHEDULES.ID.asc())
				.fetchInto(MBatchSchedulesRecord.class);
	}

	/**
	 * 有効なバッチスケジュールを優先度、ID昇順で取得する。
	 *
	 * @return 有効なバッチスケジュールレコード一覧
	 */
	public List<MBatchSchedulesRecord> findEnabled() {
		return dsl.selectFrom(MBatchSchedules.M_BATCH_SCHEDULES)
				.where(MBatchSchedules.M_BATCH_SCHEDULES.ENABLED.isTrue())
				.orderBy(MBatchSchedules.M_BATCH_SCHEDULES.PRIORITY.asc(),
						MBatchSchedules.M_BATCH_SCHEDULES.ID.asc())
				.fetchInto(MBatchSchedulesRecord.class);
	}

	/**
	 * IDに一致するバッチスケジュールを取得する。
	 *
	 * @param id バッチスケジュールID
	 * @return バッチスケジュールレコード
	 */
	public Optional<MBatchSchedulesRecord> findById(Long id) {
		return dsl.selectFrom(MBatchSchedules.M_BATCH_SCHEDULES)
				.where(MBatchSchedules.M_BATCH_SCHEDULES.ID.eq(id))
				.fetchOptionalInto(MBatchSchedulesRecord.class);
	}

	/**
	 * バッチ定義IDに紐づくスケジュールを優先度、ID昇順で取得する。
	 *
	 * @param batchDefinitionId バッチ定義ID
	 * @return バッチスケジュールレコード一覧
	 */
	public List<MBatchSchedulesRecord> findByBatchDefinitionId(Long batchDefinitionId) {
		return dsl.selectFrom(MBatchSchedules.M_BATCH_SCHEDULES)
				.where(MBatchSchedules.M_BATCH_SCHEDULES.BATCH_DEFINITION_ID.eq(batchDefinitionId))
				.orderBy(MBatchSchedules.M_BATCH_SCHEDULES.PRIORITY.asc(),
						MBatchSchedules.M_BATCH_SCHEDULES.ID.asc())
				.fetchInto(MBatchSchedulesRecord.class);
	}

	/**
	 * 対象エージェントIDに紐づく有効なスケジュールを優先度、ID昇順で取得する。
	 *
	 * @param targetAgentId 対象エージェントID
	 * @return 有効なバッチスケジュールレコード一覧
	 */
	public List<MBatchSchedulesRecord> findEnabledByTargetAgentId(Long targetAgentId) {
		return dsl.selectFrom(MBatchSchedules.M_BATCH_SCHEDULES)
				.where(MBatchSchedules.M_BATCH_SCHEDULES.TARGET_AGENT_ID.eq(targetAgentId))
				.and(MBatchSchedules.M_BATCH_SCHEDULES.ENABLED.isTrue())
				.orderBy(MBatchSchedules.M_BATCH_SCHEDULES.PRIORITY.asc(),
						MBatchSchedules.M_BATCH_SCHEDULES.ID.asc())
				.fetchInto(MBatchSchedulesRecord.class);
	}

	/**
	 * バッチスケジュールを登録する。
	 *
	 * @param record 登録するバッチスケジュール
	 * @return 登録されたバッチスケジュールID
	 */
	public Long save(MBatchSchedulesRecord databaseRecord) {
		LocalDateTime now = LocalDateTime.now(clock);
		databaseRecord.setCreatedDt(now);
		databaseRecord.setUpdatedDt(now);

		return dsl.insertInto(MBatchSchedules.M_BATCH_SCHEDULES)
				.set(MBatchSchedules.M_BATCH_SCHEDULES.BATCH_DEFINITION_ID, databaseRecord.getBatchDefinitionId())
				.set(MBatchSchedules.M_BATCH_SCHEDULES.TARGET_AGENT_ID, databaseRecord.getTargetAgentId())
				.set(MBatchSchedules.M_BATCH_SCHEDULES.CRON_EXPRESSION, databaseRecord.getCronExpression())
				.set(MBatchSchedules.M_BATCH_SCHEDULES.START_DATE, databaseRecord.getStartDate())
				.set(MBatchSchedules.M_BATCH_SCHEDULES.END_DATE, databaseRecord.getEndDate())
				.set(MBatchSchedules.M_BATCH_SCHEDULES.ALLOW_HOLIDAY_EXECUTION, databaseRecord.getAllowHolidayExecution())
				.set(MBatchSchedules.M_BATCH_SCHEDULES.ENABLED, databaseRecord.getEnabled())
				.set(MBatchSchedules.M_BATCH_SCHEDULES.PRIORITY, databaseRecord.getPriority())
				.set(MBatchSchedules.M_BATCH_SCHEDULES.CREATED_DT, databaseRecord.getCreatedDt())
				.set(MBatchSchedules.M_BATCH_SCHEDULES.UPDATED_DT, databaseRecord.getUpdatedDt())
				.returningResult(MBatchSchedules.M_BATCH_SCHEDULES.ID)
				.fetchOne(MBatchSchedules.M_BATCH_SCHEDULES.ID);
	}

	/**
	 * バッチスケジュールを更新する。
	 *
	 * @param record 更新するバッチスケジュール
	 * @return 更新件数
	 */
	public int update(MBatchSchedulesRecord databaseRecord) {
		databaseRecord.setUpdatedDt(LocalDateTime.now(clock));

		return dsl.update(MBatchSchedules.M_BATCH_SCHEDULES)
				.set(databaseRecord)
				.where(MBatchSchedules.M_BATCH_SCHEDULES.ID.eq(databaseRecord.getId()))
				.execute();
	}

	/**
	 * バッチ定義IDに紐づくスケジュールを削除する。
	 *
	 * @param batchDefinitionId バッチ定義ID
	 * @return 削除件数
	 */
	public int deleteByBatchDefinitionId(Long batchDefinitionId) {
		return dsl.deleteFrom(MBatchSchedules.M_BATCH_SCHEDULES)
				.where(MBatchSchedules.M_BATCH_SCHEDULES.BATCH_DEFINITION_ID.eq(batchDefinitionId))
				.execute();
	}
}
