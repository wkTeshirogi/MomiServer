package jp.xoki.web.zunda.smarthomewebserverbackend.device.repository;

import jakarta.validation.constraints.NotNull;
import jp.xoki.web.zunda.jooq.tables.MServices;
import jp.xoki.web.zunda.jooq.tables.records.MServicesRecord;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.time.Clock;

/** サービスマスタへアクセスするRepository。 */
@Repository
@RequiredArgsConstructor
public class ServiceRepository {
	private final DSLContext dsl;
	private final Clock clock;

	/**
	 * 登録されているサービスをID昇順で取得する。
	 *
	 * @return サービスレコード一覧
	 */
	public @NotNull List<MServicesRecord> findAll() {
		return dsl.selectFrom(jp.xoki.web.zunda.jooq.tables.MServices.M_SERVICES)
				.orderBy(MServices.M_SERVICES.ID.asc())
				.fetch().into(MServicesRecord.class);
	}

	/**
	 * IDに一致するサービスを検索する。
	 *
	 * @param id サービスID
	 * @return サービスレコード
	 */
	public Optional<MServicesRecord> findById(Long id) {
		return dsl.selectFrom(jp.xoki.web.zunda.jooq.tables.MServices.M_SERVICES)
				.where(jp.xoki.web.zunda.jooq.tables.MServices.M_SERVICES.ID.eq(id))
				.fetchOptionalInto(MServicesRecord.class);
	}

	/**
	 * サービスを登録する。
	 *
	 * @param record 登録するサービス
	 * @return 登録されたサービスID
	 */
	public Long save(MServicesRecord databaseRecord) {
		LocalDateTime now = LocalDateTime.now(clock);
		databaseRecord.setCreatedDt(now);
		databaseRecord.setUpdatedDt(now);

		return dsl.insertInto(MServices.M_SERVICES)
				.set(MServices.M_SERVICES.SERVICE_NAME, databaseRecord.getServiceName())
				.set(MServices.M_SERVICES.VIEW_NAME, databaseRecord.getViewName())
				.set(MServices.M_SERVICES.TARGET_MACHINE, databaseRecord.getTargetMachine())
				.set(MServices.M_SERVICES.STATUS, databaseRecord.getStatus())
				.set(MServices.M_SERVICES.CREATED_DT, databaseRecord.getCreatedDt())
				.set(MServices.M_SERVICES.UPDATED_DT, databaseRecord.getUpdatedDt())
				.set(MServices.M_SERVICES.DESCRIPTION, databaseRecord.getDescription())
				.set(MServices.M_SERVICES.ALLOW_START, databaseRecord.getAllowStart())
				.set(MServices.M_SERVICES.ALLOW_STOP, databaseRecord.getAllowStop())
				.set(MServices.M_SERVICES.ALLOW_RESTART, databaseRecord.getAllowRestart())
				.set(MServices.M_SERVICES.AUTO_RESTART, databaseRecord.getAutoRestart())
				.set(MServices.M_SERVICES.NOTICE_LEVEL, databaseRecord.getNoticeLevel())
				.set(MServices.M_SERVICES.LOG_PATH, databaseRecord.getLogPath())
				.set(MServices.M_SERVICES.USE_JOURNALCTL, databaseRecord.getUseJournalctl())
				.returningResult(MServices.M_SERVICES.ID)
				.fetchOne(MServices.M_SERVICES.ID);
	}

	/**
	 * サービスを更新する。
	 *
	 * @param record 更新するサービス
	 * @return 更新件数
	 */
	public int update(MServicesRecord databaseRecord) {
		databaseRecord.setUpdatedDt(LocalDateTime.now(clock));

		return dsl.update(MServices.M_SERVICES)
				.set(databaseRecord)
				.where(MServices.M_SERVICES.ID.eq(databaseRecord.getId()))
				.execute();
	}

}
