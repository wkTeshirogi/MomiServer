package jp.xoki.web.zunda.smarthomewebserverbackend.auth.repository;

import jp.xoki.web.zunda.jooq.tables.TAuthSessions;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.UUID;
import java.time.Clock;

/** 認証セッションテーブルへアクセスするRepository。 */
@Repository
@RequiredArgsConstructor
public class AuthSessionRepository {

	/** 認証セッションテーブル。 */
	private static final TAuthSessions T_AUTH_SESSIONS = TAuthSessions.T_AUTH_SESSIONS;

	/** セッションID。 */
	private static final Field<UUID> SESSION_ID = T_AUTH_SESSIONS.SESSION_ID;

	/** セッションに紐づくユーザーメールアドレス。 */
	private static final Field<String> USER_EMAIL = T_AUTH_SESSIONS.USER_EMAIL;

	/** リフレッシュトークンのハッシュ値。 */
	private static final Field<String> REFRESH_TOKEN_HASH = T_AUTH_SESSIONS.REFRESH_TOKEN_HASH;

	/** セッション有効期限。 */
	private static final Field<LocalDateTime> EXPIRES_AT = T_AUTH_SESSIONS.EXPIRES_AT;

	/** 最終利用日時。 */
	private static final Field<LocalDateTime> LAST_USED_AT = T_AUTH_SESSIONS.LAST_USED_AT;

	/** 失効済みフラグ。 */
	private static final Field<Boolean> REVOKED = T_AUTH_SESSIONS.REVOKED;

	/** 失効日時。 */
	private static final Field<LocalDateTime> REVOKED_AT = T_AUTH_SESSIONS.REVOKED_AT;

	/** 更新日時。 */
	private static final Field<LocalDateTime> UPDATED_DT = T_AUTH_SESSIONS.UPDATED_DT;

	/** jOOQ実行コンテキスト。 */
	private final DSLContext dsl;

	private final Clock clock;

	/**
	 * 認証セッションを作成する。
	 *
	 * @param sessionId セッションID
	 * @param userEmail ユーザーメールアドレス
	 * @param refreshTokenHash リフレッシュトークンハッシュ
	 * @param expiresAt 有効期限
	 */
	public void create(String sessionId, String userEmail, String refreshTokenHash, LocalDateTime expiresAt) {
		dsl.insertInto(T_AUTH_SESSIONS)
				.set(SESSION_ID, UUID.fromString(sessionId))
				.set(USER_EMAIL, userEmail)
				.set(REFRESH_TOKEN_HASH, refreshTokenHash)
				.set(EXPIRES_AT, expiresAt)
				.execute();
	}

	/**
	 * セッションIDとユーザーに一致する有効セッションが存在するか判定する。
	 *
	 * @param sessionId セッションID
	 * @param userEmail ユーザーメールアドレス
	 * @return 有効セッションが存在する場合はtrue
	 */
	public boolean isActive(String sessionId, String userEmail) {
		UUID sessionUuid = parseSessionId(sessionId);

		// UUIDとして解釈できないセッションIDは無効として扱う。
		if (sessionUuid == null) {
			return false;
		}

		return dsl.fetchExists(T_AUTH_SESSIONS,
				SESSION_ID.eq(sessionUuid)
						.and(USER_EMAIL.eq(userEmail))
						.and(REVOKED.eq(false))
						.and(EXPIRES_AT.gt(LocalDateTime.now(clock))));
	}

	/**
	 * セッションとリフレッシュトークンハッシュが現在も有効か判定する。
	 *
	 * @param sessionId セッションID
	 * @param userEmail ユーザーメールアドレス
	 * @param refreshTokenHash リフレッシュトークンハッシュ
	 * @return 有効な場合はtrue
	 */
	public boolean isRefreshTokenActive(String sessionId, String userEmail, String refreshTokenHash) {
		UUID sessionUuid = parseSessionId(sessionId);

		// UUIDとして解釈できないセッションIDは無効として扱う。
		if (sessionUuid == null) {
			return false;
		}

		return dsl.fetchExists(T_AUTH_SESSIONS,
				SESSION_ID.eq(sessionUuid)
						.and(USER_EMAIL.eq(userEmail))
						.and(REFRESH_TOKEN_HASH.eq(refreshTokenHash))
						.and(REVOKED.eq(false))
						.and(EXPIRES_AT.gt(LocalDateTime.now(clock))));
	}

	/**
	 * リフレッシュトークンを更新し、セッション有効期限を延長する。
	 *
	 * @param sessionId セッションID
	 * @param refreshTokenHash 新しいリフレッシュトークンハッシュ
	 * @param expiresAt 新しい有効期限
	 */
	public void rotateRefreshToken(String sessionId, String refreshTokenHash, LocalDateTime expiresAt) {
		UUID sessionUuid = parseSessionId(sessionId);

		// UUIDとして解釈できないセッションIDは更新対象がないため何もしない。
		if (sessionUuid == null) {
			return;
		}

		LocalDateTime now = LocalDateTime.now(clock);
		dsl.update(T_AUTH_SESSIONS)
				.set(REFRESH_TOKEN_HASH, refreshTokenHash)
				.set(EXPIRES_AT, expiresAt)
				.set(LAST_USED_AT, now)
				.set(UPDATED_DT, now)
				.where(SESSION_ID.eq(sessionUuid))
				.and(REVOKED.eq(false))
				.execute();
	}

	/**
	 * セッションを失効済みに更新する。
	 *
	 * @param sessionId セッションID
	 */
	public void revoke(String sessionId) {
		UUID sessionUuid = parseSessionId(sessionId);

		// UUIDとして解釈できないセッションIDは失効対象がないため何もしない。
		if (sessionUuid == null) {
			return;
		}

		LocalDateTime now = LocalDateTime.now(clock);
		dsl.update(T_AUTH_SESSIONS)
				.set(REVOKED, true)
				.set(REVOKED_AT, now)
				.set(UPDATED_DT, now)
				.where(SESSION_ID.eq(sessionUuid))
				.execute();
	}

	/**
	 * ユーザーに紐づく有効な認証セッションをすべて失効する。
	 *
	 * @param userEmail 対象ユーザーのメールアドレス
	 */
	public void revokeByUserEmail(String userEmail) {
		LocalDateTime now = LocalDateTime.now(clock);
		dsl.update(T_AUTH_SESSIONS)
				.set(REVOKED, true)
				.set(REVOKED_AT, now)
				.set(UPDATED_DT, now)
				.where(USER_EMAIL.eq(userEmail))
				.and(REVOKED.eq(false))
				.execute();
	}

	/**
	 * セッションID文字列をUUIDへ変換する。
	 *
	 * @param sessionId セッションID文字列
	 * @return UUIDとして有効な場合はUUID、不正な場合はnull
	 */
	private UUID parseSessionId(String sessionId) {
		if (sessionId == null) {
			return null;
		}
		try {
			return UUID.fromString(sessionId);
		} catch (IllegalArgumentException _) {
			return null;
		}
	}
}
