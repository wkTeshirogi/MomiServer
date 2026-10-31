package jp.xoki.web.zunda.smarthomewebserverbackend.batch.repository;

import jp.xoki.web.zunda.jooq.tables.MBatchDefinitionParameters;
import jp.xoki.web.zunda.jooq.tables.records.MBatchDefinitionParametersRecord;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.time.Clock;

/** バッチ定義パラメータマスタへアクセスするRepository。 */
@Repository
public class BatchDefinitionParameterRepository {
	private final DSLContext dsl;

	private final Clock clock;

	/**
	 * Repositoryを生成する。
	 *
	 * @param dsl jOOQ DSLContext
	 * @param clock 共通の業務時刻
	 */
	public BatchDefinitionParameterRepository(DSLContext dsl, Clock clock) {
		this.dsl = dsl;
		this.clock = clock;
	}

	/**
	 * バッチ定義IDに紐づくパラメータを表示順、ID昇順で取得する。
	 *
	 * @param batchDefinitionId バッチ定義ID
	 * @return バッチ定義パラメータレコード一覧
	 */
	public List<MBatchDefinitionParametersRecord> findByBatchDefinitionId(Long batchDefinitionId) {
		return dsl.selectFrom(MBatchDefinitionParameters.M_BATCH_DEFINITION_PARAMETERS)
				.where(MBatchDefinitionParameters.M_BATCH_DEFINITION_PARAMETERS.BATCH_DEFINITION_ID.eq(batchDefinitionId))
				.orderBy(MBatchDefinitionParameters.M_BATCH_DEFINITION_PARAMETERS.DISPLAY_ORDER.asc(),
						MBatchDefinitionParameters.M_BATCH_DEFINITION_PARAMETERS.ID.asc())
				.fetchInto(MBatchDefinitionParametersRecord.class);
	}

	/**
	 * 有効なバッチ定義パラメータを表示順、ID昇順で取得する。
	 *
	 * @param batchDefinitionId バッチ定義ID
	 * @return 有効なバッチ定義パラメータレコード一覧
	 */
	public List<MBatchDefinitionParametersRecord> findEnabledByBatchDefinitionId(Long batchDefinitionId) {
		return dsl.selectFrom(MBatchDefinitionParameters.M_BATCH_DEFINITION_PARAMETERS)
				.where(MBatchDefinitionParameters.M_BATCH_DEFINITION_PARAMETERS.BATCH_DEFINITION_ID.eq(batchDefinitionId))
				.and(MBatchDefinitionParameters.M_BATCH_DEFINITION_PARAMETERS.ENABLED.isTrue())
				.orderBy(MBatchDefinitionParameters.M_BATCH_DEFINITION_PARAMETERS.DISPLAY_ORDER.asc(),
						MBatchDefinitionParameters.M_BATCH_DEFINITION_PARAMETERS.ID.asc())
				.fetchInto(MBatchDefinitionParametersRecord.class);
	}

	/**
	 * IDに一致するバッチ定義パラメータを取得する。
	 *
	 * @param id バッチ定義パラメータID
	 * @return バッチ定義パラメータレコード
	 */
	public Optional<MBatchDefinitionParametersRecord> findById(Long id) {
		return dsl.selectFrom(MBatchDefinitionParameters.M_BATCH_DEFINITION_PARAMETERS)
				.where(MBatchDefinitionParameters.M_BATCH_DEFINITION_PARAMETERS.ID.eq(id))
				.fetchOptionalInto(MBatchDefinitionParametersRecord.class);
	}

	/**
	 * バッチ定義パラメータを登録する。
	 *
	 * @param record 登録するバッチ定義パラメータ
	 * @return 登録されたバッチ定義パラメータID
	 */
	public Long save(MBatchDefinitionParametersRecord databaseRecord) {
		LocalDateTime now = LocalDateTime.now(clock);
		databaseRecord.setCreatedDt(now);
		databaseRecord.setUpdatedDt(now);

		return dsl.insertInto(MBatchDefinitionParameters.M_BATCH_DEFINITION_PARAMETERS)
				.set(databaseRecord)
				.set(MBatchDefinitionParameters.M_BATCH_DEFINITION_PARAMETERS.ID,
						DSL.defaultValue(MBatchDefinitionParameters.M_BATCH_DEFINITION_PARAMETERS.ID))
				.returningResult(MBatchDefinitionParameters.M_BATCH_DEFINITION_PARAMETERS.ID)
				.fetchOne(MBatchDefinitionParameters.M_BATCH_DEFINITION_PARAMETERS.ID);
	}

	/**
	 * バッチ定義パラメータを更新する。
	 *
	 * @param record 更新するバッチ定義パラメータ
	 * @return 更新件数
	 */
	public int update(MBatchDefinitionParametersRecord databaseRecord) {
		databaseRecord.setUpdatedDt(LocalDateTime.now(clock));

		return dsl.update(MBatchDefinitionParameters.M_BATCH_DEFINITION_PARAMETERS)
				.set(databaseRecord)
				.where(MBatchDefinitionParameters.M_BATCH_DEFINITION_PARAMETERS.ID.eq(databaseRecord.getId()))
				.execute();
	}

	/**
	 * バッチ定義IDに紐づくパラメータを削除する。
	 *
	 * @param batchDefinitionId バッチ定義ID
	 * @return 削除件数
	 */
	public int deleteByBatchDefinitionId(Long batchDefinitionId) {
		return dsl.deleteFrom(MBatchDefinitionParameters.M_BATCH_DEFINITION_PARAMETERS)
				.where(MBatchDefinitionParameters.M_BATCH_DEFINITION_PARAMETERS.BATCH_DEFINITION_ID.eq(batchDefinitionId))
				.execute();
	}
}
