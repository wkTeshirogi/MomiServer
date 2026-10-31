package jp.xoki.web.zunda.smarthomewebserverbackend.common.pagination;

import java.util.List;

/** 公開DTOの形式を変更せず、先読み結果と総件数を組み立てる内部ページ。 */
public record PageSlice<T>(List<T> items, boolean hasMore, long totalCount) {

	public PageSlice {

		// 元リストの追加・削除がページへ影響しないようリスト構造を固定する。要素自体を複製する処理ではない。
		items = List.copyOf(items);
	}

	/** 先読み済みの行から表示対象だけを取り出す。
	 *
	 * 総件数はDBの件数照会結果を維持し、行数や開始位置から推測しない。
	 * @param rows 最大limit+1件の取得結果
	 * @param limit 表示件数
	 * @param totalCount 同じ検索条件で照会した総件数
	 * @param <T> 行の型
	 * @return 表示行・次ページ有無・総件数
	 */
	public static <T> PageSlice<T> fromRows(List<T> rows, int limit, long totalCount) {
		Pagination.requireLimit(limit, Integer.MAX_VALUE, "limit must be positive");

		// 余分な1行は次ページの判定専用で返却しない。範囲外ページが空でもDB総件数はそのまま保持する。
		return new PageSlice<>(rows.stream().limit(limit).toList(), rows.size() > limit, totalCount);
	}
}
