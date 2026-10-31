package jp.xoki.web.zunda.smarthomewebserverbackend.batch.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

import java.util.Arrays;

/** バッチ実行要求の状態を表す列挙型。 */
@Getter
public enum BatchExecutionStatus {

	/** 実行要求が作成され、Agentへの配信待ちである状態。 */
	QUEUED("queued", false),

	/** Agentへ実行要求を配信済みである状態。 */
	DISPATCHED("dispatched", false),

	/** Agent側で実行中である状態。 */
	RUNNING("running", false),

	/** バッチ実行が正常終了した状態。 */
	SUCCESS("success", true),

	/** バッチ実行が失敗終了した状態。 */
	FAILED("failed", true),

	/** バッチ実行がタイムアウトした状態。 */
	TIMEOUT("timeout", true),

	/** バッチ実行がキャンセルされた状態。 */
	CANCELLED("cancelled", true),

	/** 未開始のまま開始期限を超えたことが確認できた状態。 */
	EXPIRED("expired", true);

	/** DB保存およびAPI入出力で利用する実行状態名。 */
	private final String storedName;

	/** 完了結果として利用できる状態かどうか。 */
	private final boolean terminal;

	BatchExecutionStatus(String storedName, boolean terminal) {
		this.storedName = storedName;
		this.terminal = terminal;
	}

	/**
	 * API入力値またはDB保存名からバッチ実行状態へ変換する。
	 *
	 * @param value バッチ実行状態名
	 * @return 対応するバッチ実行状態
	 */
	@JsonCreator
	public static BatchExecutionStatus from(String value) {

		// 未指定値はBean Validation側で必須エラーとして扱う。
		if (value == null || value.isBlank()) {
			return null;
		}

		return Arrays.stream(values())
				.filter(status -> status.name().equalsIgnoreCase(value)
						|| status.storedName.equalsIgnoreCase(value))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unknown batch execution status: " + value));
	}

	/**
	 * JSON出力時にDB保存名と同じ実行状態名を返す。
	 *
	 * @return JSONへ出力する実行状態名
	 */
	@JsonValue
	public String toJson() {
		return storedName;
	}
}
