package jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.model;

import java.time.LocalDate;

/** 車検期限通知の段階。停止期間の通知は現在該当する段階だけを補完する。 */
public enum VehicleInspectionNotificationStage {
	TWO_MONTHS("2か月前"),
	ONE_MONTH("1か月前"),
	SEVEN_DAYS("7日前"),
	DUE_TODAY("満了日当日"),
	EXPIRED("期限切れ");

	private final String label;

	VehicleInspectionNotificationStage(String label) { this.label = label; }

	public String getLabel() { return label; }

	/** 通知段階の判定
	 *
	 * 月単位の閾値は満了日から暦月を減算し、月末はその月の末日へ丸める。
	 *
	 * @param expiryDate 車検満了日
	 * @param today 日本時間の判定日
	 * @return 該当する最新段階。通知期間外はnull
	 */
	public static VehicleInspectionNotificationStage resolve(LocalDate expiryDate, LocalDate today) {
		if (expiryDate == null) return null;
		if (today.isAfter(expiryDate)) return EXPIRED;
		if (today.isEqual(expiryDate)) return DUE_TODAY;
		if (!today.isBefore(expiryDate.minusDays(7))) return SEVEN_DAYS;
		if (!today.isBefore(expiryDate.minusMonths(1))) return ONE_MONTH;
		if (!today.isBefore(expiryDate.minusMonths(2))) return TWO_MONTHS;
		return null;
	}
}
