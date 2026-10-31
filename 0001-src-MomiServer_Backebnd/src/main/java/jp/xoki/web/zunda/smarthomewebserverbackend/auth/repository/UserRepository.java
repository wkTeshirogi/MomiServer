package jp.xoki.web.zunda.smarthomewebserverbackend.auth.repository;

import static jp.xoki.web.zunda.jooq.Tables.M_USERS;

import jakarta.validation.constraints.NotNull;
import jp.xoki.web.zunda.jooq.tables.records.MUsersRecord;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;
import java.time.Clock;
import org.jooq.UpdateSetMoreStep;

/** ユーザーマスタへアクセスするRepository。 */
@Repository
@RequiredArgsConstructor
public class UserRepository {
	private final DSLContext dsl;

	private final Clock clock;

	/**
	 * メールアドレスに一致するユーザーを検索する。
	 *
	 * @param email 検索対象のメールアドレス
	 * @return ユーザーレコード
	 */
	public Optional<MUsersRecord> findByEmail(String email) {
		return dsl.selectFrom(M_USERS)
				.where(M_USERS.EMAIL.eq(email))
				.fetchOptional();
	}

	/** パスワード変更対象のユーザーをロックし、同時変更の認証値の取り違えを防ぐ。
	 *
	 * @param email 認証済みの本人メールアドレス
	 * @return 行ロック済みユーザー
	 */
	public Optional<MUsersRecord> findByEmailForUpdate(String email) {
		return dsl.selectFrom(M_USERS).where(M_USERS.EMAIL.eq(email)).forUpdate().fetchOptional();
	}

	/** 行ロック下で認証の実行状態を保存する。設定値やユーザーの管理ロールは変更しない。
	 *
	 * @param id 対象ユーザー
	 * @param failures 連続失敗回数
	 * @param lockedUntil 解除時刻。未ロックはnull
	 */
	public void updateLoginState(Long id, int failures, LocalDateTime lockedUntil) {
		dsl.update(M_USERS).set(M_USERS.LOGIN_FAILURE_COUNT, failures)
				.set(M_USERS.LOGIN_LOCKED_UNTIL, lockedUntil).where(M_USERS.ID.eq(id)).execute();
	}

	/** 現在のADMINロールだけを通知先として解決する。
	 *
	 * @return 管理者ID一覧
	 */
	public List<Long> findAdminIds() {
		return dsl.select(M_USERS.ID).from(M_USERS)
				.where(M_USERS.ROLE.eq(jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.RoleEnum.ADMIN_ROLE.getStoredName()))
				.fetch(M_USERS.ID);
	}

	/** 本人の表示名だけを更新する。
	 *
	 * @param email 認証済みの本人メールアドレス
	 * @param username 表示名
	 */
	public void updatePersonalUsername(String email, String username) {
		dsl.update(M_USERS).set(M_USERS.USERNAME, username).set(M_USERS.UPDATED_DT, LocalDateTime.now(clock))
				.where(M_USERS.EMAIL.eq(email)).execute();
	}

	/** 本人のテーマだけを更新する。
	 *
	 * @param email 認証済みの本人メールアドレス
	 * @param theme 保存用テーマコード
	 */
	public void updatePersonalTheme(String email, String theme) {
		dsl.update(M_USERS).set(M_USERS.THEME, theme).set(M_USERS.UPDATED_DT, LocalDateTime.now(clock))
				.where(M_USERS.EMAIL.eq(email)).execute();
	}

	/** 本人のハッシュ化済みパスワードだけを更新する。
	 *
	 * @param email 認証済みの本人メールアドレス
	 * @param encodedPassword ハッシュ化済みパスワード
	 */
	public void updatePersonalPassword(String email, String encodedPassword) {
		dsl.update(M_USERS).set(M_USERS.PASSWORD, encodedPassword).set(M_USERS.UPDATED_DT, LocalDateTime.now(clock))
				.where(M_USERS.EMAIL.eq(email)).execute();
	}

	/**
	 * IDに一致するユーザーを検索する。
	 *
	 * @param id ユーザーID
	 * @return ユーザーレコード
	 */
	public Optional<MUsersRecord> findById(Long id) {
		return dsl.selectFrom(M_USERS)
				.where(M_USERS.ID.eq(id))
				.fetchOptional();
	}

	/**
	 * すべてのユーザーをID昇順で取得する。
	 *
	 * @return ユーザーレコード一覧
	 */
	public @NotNull List<MUsersRecord> findAll() {
		return dsl.selectFrom(M_USERS)
				.orderBy(M_USERS.ID.asc())
				.fetch()
				.into(MUsersRecord.class);
	}

	/**
	 * ユーザーを新規登録する。
	 *
	 * @param email メールアドレス
	 * @param password ハッシュ化済みパスワード
	 * @param role 保存する権限名
	 */
	public void save(String email, String password, String role) {
		dsl.insertInto(M_USERS)
				.set(M_USERS.EMAIL, email)
				.set(M_USERS.PASSWORD, password)
				.set(M_USERS.ROLE, role)
				.execute();
	}

	/**
	 * メールアドレスに一致するユーザーのパスワードと権限を更新する。
	 *
	 * @param email 更新対象のメールアドレス
	 * @param password ハッシュ化済みパスワード。nullの場合は既存値を維持する
	 * @param role 更新後の権限
	 * @return 更新件数
	 */
	public int updateSettings(String email, String password, String role) {
		UpdateSetMoreStep<MUsersRecord> update = dsl.update(M_USERS)
				.set(M_USERS.ROLE, role);

		if (password != null) {
			update.set(M_USERS.PASSWORD, password);
		}

		return update.where(M_USERS.EMAIL.eq(email))
				.execute();
	}
}
