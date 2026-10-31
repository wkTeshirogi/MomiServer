package jp.xoki.web.zunda.smarthomewebserverbackend.batch.service;

import jp.xoki.web.zunda.jooq.tables.records.MBatchDefinitionsRecord;
import jp.xoki.web.zunda.jooq.tables.records.MBatchSchedulesRecord;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.repository.BatchDefinitionRepository;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.repository.BatchScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.Clock;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.dto.BatchExecutionRequestDetailDto;

/** 当日・翌日のバッチ実行予定を要求へ展開するService。
 *
 * 定期実行の登録とは分離し、起動時の補完でも同じ有効期間・重複判定を適用する。
 */
@RequiredArgsConstructor
@Slf4j
@Service
public class BatchSchedulePlanningService {

	/** バッチスケジュールRepository。 */
	private final BatchScheduleRepository batchScheduleRepository;

	/** バッチ定義Repository。 */
	private final BatchDefinitionRepository batchDefinitionRepository;

	/** バッチ実行要求Service。 */
	private final BatchExecutionRequestService batchExecutionRequestService;

	private final Clock clock;

	private record PlanningResult(int created, int duplicates, int failed) {
		private PlanningResult add(PlanningResult other) {
			return new PlanningResult(created + other.created, duplicates + other.duplicates, failed + other.failed);
		}
	}

	/** バッチ処理: スケジュール実行要求の作成。
	 *
	 * 毎日00:00とアプリケーション起動完了時に、当日・翌日の未作成分を登録する。
	 * 過去時刻の未作成分は期限切れとして記録し、実行しない。日時は共通Clockのタイムゾーンを使用する。
	 */
	public void enqueueScheduledBatches() {
		enqueueScheduledBatches(LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS));
	}

	/** 当日・翌日の実行予定展開。
	 *
	 * Cron式に一致する各予定日時を展開し、既存要求を変更せず未作成分のみ登録する。
	 * 定義や予定ごとの失敗を記録し、他のスケジュールの作成を継続する。
	 *
	 * @param planningTime 作成処理の基準日時。これより前の予定は実行しない
	 */
	void enqueueScheduledBatches(LocalDateTime planningTime) {

		// 起動時も当日0時から走査し、当日の欠落分と翌日分を同じ範囲で補う。過去予定の実行可否は作成側が判定する。
		LocalDateTime from = planningTime.toLocalDate().atStartOfDay();
		LocalDateTime until = from.plusDays(2);
		int created = 0;
		int duplicates = 0;
		int failed = 0;
		log.info("Batch request planning started. from={}, until={}", from, until);
		for (MBatchSchedulesRecord schedule : batchScheduleRepository.findEnabled()) {
			PlanningResult result = planSchedule(schedule, from, until, planningTime);
			created += result.created();
			duplicates += result.duplicates();
			failed += result.failed();
		}
		log.info("Batch request planning completed. from={}, until={}, created={}, duplicates={}, failed={}",
				from, until, created, duplicates, failed);
	}

	private PlanningResult planSchedule(MBatchSchedulesRecord schedule, LocalDateTime from,
			LocalDateTime until, LocalDateTime planningTime) {
		PlanningResult result = new PlanningResult(0, 0, 0);
		try {
			MBatchDefinitionsRecord definition = batchDefinitionRepository.findById(schedule.getBatchDefinitionId())
					.orElse(null);
			if (definition == null || Boolean.FALSE.equals(definition.getEnabled())) {
				return result;
			}
			CronExpression cron = CronExpression.parse(normalizeCron(schedule.getCronExpression()));

		// nextは指定時刻より後を返すため1秒戻し、当日00:00:00ちょうどの予定も取りこぼさない。
			for (LocalDateTime fireTime = cron.next(from.minusSeconds(1));
					fireTime != null && fireTime.isBefore(until); fireTime = cron.next(fireTime)) {
				if (!isInActivePeriod(schedule, fireTime.toLocalDate())) {
					continue;
				}
				result = result.add(createOccurrence(schedule, definition, fireTime, planningTime));
			}
			return result;
		} catch (RuntimeException exception) {
			log.warn("Failed to plan batch schedule. scheduleId={}", schedule.getId(), exception);
			return result.add(new PlanningResult(0, 0, 1));
		}
	}

	private PlanningResult createOccurrence(MBatchSchedulesRecord schedule, MBatchDefinitionsRecord definition,
			LocalDateTime fireTime, LocalDateTime planningTime) {
		try {
			BatchExecutionRequestDetailDto request = batchExecutionRequestService.createScheduledRequest(schedule, definition, fireTime, planningTime);
			return request == null ? new PlanningResult(0, 1, 0) : new PlanningResult(1, 0, 0);
		} catch (RuntimeException exception) {
			log.warn("Failed to create scheduled batch request. scheduleId={}, fireTime={}",
					schedule.getId(), fireTime, exception);
			return new PlanningResult(0, 0, 1);
		}
	}

	/** スケジュールの有効期間判定。
	 *
	 * 判定日が開始日から終了日までの範囲に含まれるか確認する。開始日と終了日の当日も対象とする。
	 *
	 * @param schedule バッチスケジュール
	 * @param today 判定日
	 * @return 有効期間内の場合はtrue
	 */
	private boolean isInActivePeriod(MBatchSchedulesRecord schedule, LocalDate today) {

		// 開始日前のスケジュールはまだ実行対象にしない。
		if (schedule.getStartDate() != null && today.isBefore(schedule.getStartDate())) {
			return false;
		}
		return schedule.getEndDate() == null || !today.isAfter(schedule.getEndDate());
	}

	/** Cron式の形式補正。
	 *
	 * 前後の空白を除去し、5フィールドのCron式には秒フィールドの0を補ってSpring形式へ変換する。
	 *
	 * @param cronExpression DBに保存されたcron式
	 * @return Spring CronExpressionへ渡すcron式
	 */
	private String normalizeCron(String cronExpression) {
		String cron = cronExpression == null ? "" : cronExpression.trim();

		// UNIX cron形式の場合は秒フィールドを先頭に補う。
		if (cron.split("\\s+").length == 5) {
			return "0 " + cron;
		}
		return cron;
	}
}
