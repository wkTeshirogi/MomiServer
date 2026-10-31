package jp.xoki.web.zunda.smarthomewebserverbackend.external.repository;

import jp.xoki.web.zunda.smarthomewebserverbackend.external.dto.ExternalApiKeyDto;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Field;
import org.jooq.impl.SQLDataType;
import static jp.xoki.web.zunda.jooq.Tables.T_EXTERNAL_API_KEYS;
import static jp.xoki.web.zunda.jooq.Tables.T_EXTERNAL_API_KEY_EVENTS;
import static org.jooq.impl.DSL.function;
import static org.jooq.impl.DSL.currentOffsetDateTime;
import static org.jooq.impl.DSL.trunc;
import static org.jooq.impl.DSL.when;
import org.jooq.DatePart;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** APIキー管理とプロセス間で共有する時間単位レート制限。 */
@Repository
@RequiredArgsConstructor
public class ExternalApiKeyRepository {
	private final DSLContext dsl;

	/** キー情報一覧。
	 * @return 秘密値を含まない一覧
	 */
	public List<ExternalApiKeyDto> list() {
		return dsl.selectFrom(T_EXTERNAL_API_KEYS).orderBy(T_EXTERNAL_API_KEYS.CREATED_AT.desc(), T_EXTERNAL_API_KEYS.ID).fetch(this::map);
	}

	/** キー情報取得。
	 * @param id キーID
	 * @return 管理情報
	 */
	public Optional<ExternalApiKeyDto> find(UUID id) {
		return dsl.selectFrom(T_EXTERNAL_API_KEYS).where(T_EXTERNAL_API_KEYS.ID.eq(id)).fetchOptional(this::map);
	}

	/** ハッシュ照合。
	 * @param hash 提示された秘密値のハッシュ
	 * @return 管理情報
	 */
	public Optional<ExternalApiKeyDto> findByHash(String hash) {
		return dsl.selectFrom(T_EXTERNAL_API_KEYS).where(T_EXTERNAL_API_KEYS.KEY_HASH.eq(hash)).fetchOptional(this::map);
	}

	/** 発行情報保存。
	 * @param id キーID
	 * @param name 連携先名
	 * @param prefix 識別用の先頭部分
	 * @param hash 秘密値ハッシュ
	 * @param scopes 許可スコープ
	 * @param expiry 有効期限
	 * @param actor 発行者
	 */
	public void insert(UUID id, String name, String prefix, String hash, Set<String> scopes,
			OffsetDateTime expiry, String actor) {
		dsl.insertInto(T_EXTERNAL_API_KEYS).set(T_EXTERNAL_API_KEYS.ID, id)
                .set(T_EXTERNAL_API_KEYS.NAME, name).set(T_EXTERNAL_API_KEYS.KEY_PREFIX, prefix)
                .set(T_EXTERNAL_API_KEYS.KEY_HASH, hash).set(T_EXTERNAL_API_KEYS.SCOPES, scopes.toArray(String[]::new))
                .set(T_EXTERNAL_API_KEYS.EXPIRES_AT, expiry).set(T_EXTERNAL_API_KEYS.CREATED_BY, actor).execute();
	}

	/** 不可逆な無効化。
	 * @param id キーID
	 * @param actor 操作者
	 * @return 初回無効化の場合true
	 */
	public boolean revoke(UUID id, String actor) {
		return dsl.update(T_EXTERNAL_API_KEYS).set(T_EXTERNAL_API_KEYS.STATUS, "REVOKED")
                .set(T_EXTERNAL_API_KEYS.REVOKED_AT, currentOffsetDateTime()).set(T_EXTERNAL_API_KEYS.REVOKED_BY, actor)
                .where(T_EXTERNAL_API_KEYS.ID.eq(id)).and(T_EXTERNAL_API_KEYS.STATUS.eq("ACTIVE")).execute() == 1;
	}

	/** 監査履歴保存。
	 * @param id キーID
	 * @param event イベント種別
	 * @param actor 操作者
	 */
	public void event(UUID id, String event, String actor) {
		dsl.insertInto(T_EXTERNAL_API_KEY_EVENTS).set(T_EXTERNAL_API_KEY_EVENTS.API_KEY_ID, id)
                .set(T_EXTERNAL_API_KEY_EVENTS.EVENT_TYPE, event).set(T_EXTERNAL_API_KEY_EVENTS.ACTOR, actor).execute();
	}

	/** DB原子更新による利用回数消費。
	 * @param id キーID
	 * @param limit 時間あたり上限
	 * @return 消費後回数。失効または上限到達時は空
	 */
	public Optional<Integer> consume(UUID id, int limit) {
		// 利用日時・失効判定は実時刻、レート制限の時間窓はDBトランザクション時刻を維持する。
        Field<OffsetDateTime> actualNow = function("clock_timestamp", SQLDataType.TIMESTAMPWITHTIMEZONE);
        Field<OffsetDateTime> window = trunc(currentOffsetDateTime(), DatePart.HOUR);
        return dsl.update(T_EXTERNAL_API_KEYS).set(T_EXTERNAL_API_KEYS.LAST_USED_AT, actualNow)
                .set(T_EXTERNAL_API_KEYS.REQUEST_COUNT, when(T_EXTERNAL_API_KEYS.REQUEST_WINDOW.eq(window),
                        T_EXTERNAL_API_KEYS.REQUEST_COUNT.add(1)).otherwise(1))
                .set(T_EXTERNAL_API_KEYS.REQUEST_WINDOW, window).where(T_EXTERNAL_API_KEYS.ID.eq(id))
                .and(T_EXTERNAL_API_KEYS.STATUS.eq("ACTIVE"))
                .and(T_EXTERNAL_API_KEYS.EXPIRES_AT.isNull().or(T_EXTERNAL_API_KEYS.EXPIRES_AT.gt(actualNow)))
                .and(T_EXTERNAL_API_KEYS.REQUEST_WINDOW.isDistinctFrom(window).or(T_EXTERNAL_API_KEYS.REQUEST_COUNT.lt(limit)))
                .returningResult(T_EXTERNAL_API_KEYS.REQUEST_COUNT).fetchOptional(T_EXTERNAL_API_KEYS.REQUEST_COUNT);
	}

	private ExternalApiKeyDto map(Record result) {
        return new ExternalApiKeyDto(result.get(T_EXTERNAL_API_KEYS.ID), result.get(T_EXTERNAL_API_KEYS.NAME),
                result.get(T_EXTERNAL_API_KEYS.KEY_PREFIX),
                new HashSet<>(Arrays.asList(result.get(T_EXTERNAL_API_KEYS.SCOPES))),
                result.get(T_EXTERNAL_API_KEYS.STATUS), result.get(T_EXTERNAL_API_KEYS.EXPIRES_AT),
                result.get(T_EXTERNAL_API_KEYS.LAST_USED_AT), result.get(T_EXTERNAL_API_KEYS.CREATED_AT),
                result.get(T_EXTERNAL_API_KEYS.CREATED_BY), result.get(T_EXTERNAL_API_KEYS.REVOKED_AT),
                result.get(T_EXTERNAL_API_KEYS.REVOKED_BY));
    }
}
