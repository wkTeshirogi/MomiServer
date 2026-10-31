package jp.xoki.web.zunda.smarthomewebserverbackend.common.pagination;

import java.util.Set;

/** APIごとの上限・補正方針・文言を維持するページングの共通処理。 */
public final class Pagination {

	private Pagination() {
	}

	/** 開始位置の検証。
	 *
	 * @param offset 開始位置
	 * @param message 不正な場合の既存API文言
	 * @return 検証済み開始位置
	 */
	public static int requireOffset(int offset, String message) {
		if (offset < 0) throw new IllegalArgumentException(message);
		return offset;
	}

	/** 範囲指定の件数検証。
	 *
	 * @param limit 件数
	 * @param maximum API固有の上限
	 * @param message 不正な場合の既存API文言
	 * @return 検証済み件数
	 */
	public static int requireLimit(int limit, int maximum, String message) {
		if (limit < 1 || limit > maximum) throw new IllegalArgumentException(message);
		return limit;
	}

	/** 選択肢指定の件数検証。
	 *
	 * @param limit 件数
	 * @param allowed API固有の選択肢
	 * @param message 不正な場合の既存API文言
	 * @return 検証済み件数
	 */
	public static int requireAllowedLimit(int limit, Set<Integer> allowed, String message) {
		if (limit < 1 || !allowed.contains(limit)) throw new IllegalArgumentException(message);
		return limit;
	}

	/** 補正を許可するAPI向けの件数決定。
	 *
	 * @param limit 任意の指定件数
	 * @param defaultLimit 未指定時の件数
	 * @param maximum API固有の上限
	 * @return 未指定時は既定値、指定時は1から上限へ補正した件数
	 */
	public static int clampLimit(Integer limit, int defaultLimit, int maximum) {
		return limit == null ? defaultLimit : Math.clamp(limit, 1, maximum);
	}

	/** 次ページ判定用の先読み件数。
	 *
	 * @param limit 検証済みの表示件数
	 * @return 表示件数より1件多い取得件数
	 */
	public static int lookAheadLimit(int limit) {
		if (limit < 1) throw new IllegalArgumentException("limit must be positive");
		return Math.incrementExact(limit);
	}
}
