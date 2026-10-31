package jp.xoki.web.zunda.smarthomewebserverbackend.common.repository;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.SQLDataType;
import java.time.OffsetDateTime;
import static jp.xoki.web.zunda.jooq.Tables.M_API_VERSIONS;
import static org.jooq.impl.DSL.function;
import static org.jooq.impl.DSL.val;
import static org.jooq.impl.DSL.coalesce;
import static org.jooq.impl.DSL.extract;
import org.jooq.DatePart;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** 外部連携APIのバージョン公開状態を取得するRepository。 */
@Repository
@RequiredArgsConstructor
public class ApiVersionStatusRepository {

	private final DSLContext dsl;

	/**
	 * APIグループとバージョンの公開状態を取得する。
	 *
	 * @param apiGroup AgentまたはIoT
	 * @param version APIパス上のバージョン
	 * @return 登録済みの公開状態
	 */
	public Optional<ApiVersionStatus> find(String apiGroup, String version) {
		Field<OffsetDateTime> deprecatedAt = function("timezone", SQLDataType.TIMESTAMPWITHTIMEZONE,
                val("Asia/Tokyo"), coalesce(M_API_VERSIONS.DEPRECATED_DT, M_API_VERSIONS.UPDATED_DT));
        // PostgreSQLのBIGINTキャストによる秒の丸めを維持し、Java側で切り捨てない。
        Field<Long> deprecatedEpoch = extract(deprecatedAt, DatePart.EPOCH).cast(Long.class);
        return dsl.select(M_API_VERSIONS.STATUS, deprecatedEpoch).from(M_API_VERSIONS)
                .where(M_API_VERSIONS.API_GROUP.eq(apiGroup)).and(M_API_VERSIONS.VERSION.eq(version)).limit(1)
                .fetchOptional(row -> new ApiVersionStatus(row.value1(), row.value2() == null ? 0L : row.value2()));
	}

	/** DB上の公開状態と非推奨日時（Unix秒）。 */
	public record ApiVersionStatus(String status, long deprecatedEpochSeconds) {
	}
}
