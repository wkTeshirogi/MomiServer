package jp.xoki.web.zunda.smarthomewebserverbackend.batch.constants;

import java.util.Set;

/** BatchScheduleServiceで使用する固定値。カテゴリ内の利用元から参照する。 */
public final class BatchScheduleServiceConstants {

	public static final Set<Integer> LOG_PAGE_SIZES = Set.of(20, 50, 100);

	private BatchScheduleServiceConstants() {
	}
}
