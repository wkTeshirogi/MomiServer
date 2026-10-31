package jp.xoki.web.zunda.smarthomewebserverbackend.common.repository;

import jp.xoki.web.zunda.jooq.tables.TApiAccessLogs;

import jp.xoki.web.zunda.smarthomewebserverbackend.common.pagination.Pagination;

import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.ApiAccessLogDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.ApiAccessLogSearchRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.ApiAccessLogWriteRequest;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** APIアクセスログの登録・検索Repository。 */
@Repository
@RequiredArgsConstructor
public class ApiAccessLogRepository {

	private static final TApiAccessLogs TABLE = TApiAccessLogs.T_API_ACCESS_LOGS;
	private static final Field<Long> ID = TABLE.ID;
	private static final Field<UUID> REQUEST_ID = TABLE.REQUEST_ID;
	private static final Field<OffsetDateTime> OCCURRED_AT =
			TABLE.OCCURRED_AT;
	private static final Field<String> HTTP_METHOD = TABLE.HTTP_METHOD;
	private static final Field<String> REQUEST_PATH = TABLE.REQUEST_PATH;
	private static final Field<String> ENDPOINT_PATTERN =
			TABLE.ENDPOINT_PATTERN;
	// DBのSMALLINTを、既存のAPI・DTOが使用するIntegerとして扱う（SQL上の列は変更しない）。
	private static final Field<Integer> STATUS_CODE = TABLE.STATUS_CODE.coerce(Integer.class);
	private static final Field<Long> DURATION_MS = TABLE.DURATION_MS;
	private static final Field<String> SOURCE_TYPE = TABLE.SOURCE_TYPE;
	private static final Field<String> PRINCIPAL = TABLE.PRINCIPAL;
	private static final Field<String> IP_ADDRESS = TABLE.IP_ADDRESS;
	private static final Field<String> USER_AGENT = TABLE.USER_AGENT;
	private static final Field<String> ERROR_CODE = TABLE.ERROR_CODE;

	private final org.jooq.DSLContext dsl;

	/** APIアクセスログ登録。
	 *
	 * フィルターで収集した機密情報を含まないアクセス情報を追記する。
	 *
	 * @param request 登録するアクセスログ
	 */
	public void insert(ApiAccessLogWriteRequest request) {
		dsl.insertInto(TABLE)
				.set(REQUEST_ID, request.request_id())
				.set(OCCURRED_AT, request.occurred_at())
				.set(HTTP_METHOD, request.http_method())
				.set(REQUEST_PATH, request.request_path())
				.set(ENDPOINT_PATTERN, request.endpoint_pattern())
				.set(STATUS_CODE, request.status_code())
				.set(DURATION_MS, request.duration_ms())
				.set(SOURCE_TYPE, request.source_type())
				.set(PRINCIPAL, request.principal())
				.set(IP_ADDRESS, request.ip_address())
				.set(USER_AGENT, request.user_agent())
				.set(ERROR_CODE, request.error_code())
				.execute();
	}

	/** APIアクセスログ一覧検索。
	 *
	 * 検索条件を組み立て、発生日時の新しい順で取得する。
	 *
	 * @param request 正規化済み検索条件
	 * @return 条件に一致するログ
	 */
	public List<ApiAccessLogDetailDto> search(ApiAccessLogSearchRequest request) {
		Condition condition = searchCondition(request);

		return dsl.select(ID, REQUEST_ID, OCCURRED_AT, HTTP_METHOD, REQUEST_PATH,
					ENDPOINT_PATTERN, STATUS_CODE, DURATION_MS, SOURCE_TYPE, PRINCIPAL,
					IP_ADDRESS, USER_AGENT, ERROR_CODE)
				.from(TABLE)
				.where(condition)
				.orderBy(OCCURRED_AT.desc(), ID.desc())
				.limit(Pagination.lookAheadLimit(request.limit()))
				.offset(request.offset())
				.fetch(this::toDto);
	}

	/** APIアクセスログ検索条件に一致する総件数を取得する。 */
	public long count(ApiAccessLogSearchRequest request) {
		return dsl.fetchCount(TABLE, searchCondition(request));
	}

	private Condition searchCondition(ApiAccessLogSearchRequest request) {
		Condition condition = OCCURRED_AT.between(request.from(), request.to());
		if (request.http_method() != null) {
			condition = condition.and(HTTP_METHOD.eq(request.http_method()));
		}
		if (request.request_path() != null) {
			condition = condition.and(
					REQUEST_PATH.containsIgnoreCase(request.request_path())
							.or(ENDPOINT_PATTERN.containsIgnoreCase(request.request_path())));
		}
		if (request.status_code() != null) {
			condition = condition.and(STATUS_CODE.eq(request.status_code()));
		} else if (request.status_category() != null) {
			condition = condition.and(statusCategory(request.status_category()));
		}
		if (request.source_type() != null) {
			condition = condition.and(SOURCE_TYPE.eq(request.source_type()));
		}
		if (request.principal() != null) {
			condition = condition.and(PRINCIPAL.containsIgnoreCase(request.principal()));
		}
		if (request.ip_address() != null) {
			condition = condition.and(IP_ADDRESS.eq(request.ip_address()));
		}
		if (request.min_duration_ms() != null) {
			condition = condition.and(DURATION_MS.ge(request.min_duration_ms()));
		}
		return condition;
	}

	/** APIアクセスログ詳細取得。
	 *
	 * 外部参照用リクエストIDでアクセスログを検索する。
	 *
	 * @param requestId リクエストID
	 * @return 対象ログ。存在しない場合は空
	 */
	public Optional<ApiAccessLogDetailDto> findByRequestId(UUID requestId) {
		return dsl.select(ID, REQUEST_ID, OCCURRED_AT, HTTP_METHOD, REQUEST_PATH,
					ENDPOINT_PATTERN, STATUS_CODE, DURATION_MS, SOURCE_TYPE, PRINCIPAL,
					IP_ADDRESS, USER_AGENT, ERROR_CODE)
				.from(TABLE)
				.where(REQUEST_ID.eq(requestId))
				.fetchOptional(this::toDto);
	}

	/** 期限切れAPIアクセスログ削除。
	 *
	 * 保持期限より前に発生したアクセスログをまとめて削除する。
	 *
	 * @param cutoff 保持期限
	 * @return 削除件数
	 */
	public int deleteOccurredBefore(OffsetDateTime cutoff) {
		return dsl.deleteFrom(TABLE).where(OCCURRED_AT.lt(cutoff)).execute();
	}

	private Condition statusCategory(String category) {
		return switch (category) {
			case "success" -> STATUS_CODE.between(200, 399);
			case "client_error" -> STATUS_CODE.between(400, 499);
			case "server_error" -> STATUS_CODE.between(500, 599);
			default -> DSL.falseCondition();
		};
	}

	private ApiAccessLogDetailDto toDto(Record logRecord) {
		return new ApiAccessLogDetailDto(
				logRecord.get(ID), logRecord.get(REQUEST_ID), logRecord.get(OCCURRED_AT),
				logRecord.get(HTTP_METHOD), logRecord.get(REQUEST_PATH), logRecord.get(ENDPOINT_PATTERN),
				logRecord.get(STATUS_CODE), logRecord.get(DURATION_MS), logRecord.get(SOURCE_TYPE),
				logRecord.get(PRINCIPAL), logRecord.get(IP_ADDRESS), logRecord.get(USER_AGENT),
				logRecord.get(ERROR_CODE));
	}
}
