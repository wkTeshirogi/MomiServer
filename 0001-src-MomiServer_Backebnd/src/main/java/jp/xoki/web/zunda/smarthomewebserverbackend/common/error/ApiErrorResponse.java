package jp.xoki.web.zunda.smarthomewebserverbackend.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.time.Clock;

/** APIエラーをクライアントへ返すための共通レスポンス。 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiErrorResponse(OffsetDateTime timestamp, int status, String error,
		String code, String message, String path, List<FieldErrorDetail> fieldErrors) {
	public ApiErrorResponse {
		fieldErrors = fieldErrors == null ? null : Collections.unmodifiableList(new ArrayList<>(fieldErrors));
	}

	/**
	 * フィールドエラーなしのAPIエラーレスポンスを生成する。
	 *
	 * @param status HTTPステータスコード
	 * @param error HTTPステータス理由句
	 * @param code アプリケーション固有エラーコード
	 * @param message エラーメッセージ
	 * @param path エラー発生パス
	 * @param clock タイムスタンプ取得用の時計
	 * @return APIエラーレスポンス
	 */
	public static ApiErrorResponse of(int status, String error, String code,
			String message, String path, Clock clock) {
		return of(status, error, code, message, path, List.of(), clock);
	}

	/**
	 * フィールドエラーを含むAPIエラーレスポンスを生成する。
	 *
	 * @param status HTTPステータスコード
	 * @param error HTTPステータス理由句
	 * @param code アプリケーション固有エラーコード
	 * @param message エラーメッセージ
	 * @param path エラー発生パス
	 * @param fieldErrors 項目別エラー一覧
	 * @param clock タイムスタンプ取得用の時計
	 * @return APIエラーレスポンス
	 */
	public static ApiErrorResponse of(int status, String error, String code,
			String message, String path, List<FieldErrorDetail> fieldErrors, Clock clock) {
		return new ApiErrorResponse(OffsetDateTime.now(clock), status, error, code,
				message, path, fieldErrors);
	}

	/** 入力バリデーションに失敗したフィールドとメッセージの組み合わせ。 */
	public record FieldErrorDetail(String field, String message) { }
}
