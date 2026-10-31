package jp.xoki.web.zunda.smarthomewebserverbackend.common.security;

import static jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.SecurityAuthorityConstants.ROLE_PREFIX;


import jp.xoki.web.zunda.jooq.tables.records.MBatchDefinitionsRecord;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.repository.BatchDefinitionRepository;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.RoleEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

/** バッチ定義単位の実行可否を判定するサービス。 */
@Service
@RequiredArgsConstructor
public class BatchPermissionService {

	/** バッチ定義のrequired_roleを取得するRepository。 */
	private final BatchDefinitionRepository batchDefinitionRepository;

	/**
	 * 指定されたバッチ定義を現在の認証ユーザーが実行できるか判定する。
	 *
	 * @param authentication 認証情報
	 * @param batchDefinitionId バッチ定義ID
	 * @return 実行可能な場合はtrue
	 */
	public boolean canExecute(Authentication authentication, Long batchDefinitionId) {

		// 未認証の場合はバッチ実行を許可しない。
		if (authentication == null || !authentication.isAuthenticated()) {
			return false;
		}

		// 管理者はバッチ定義ごとのrequired_roleに関係なく実行できる。
		if (hasRole(authentication, RoleEnum.ADMIN_ROLE.getStoredName())) {
			return true;
		}

		// 対象バッチ定義を特定できない場合は個別権限を判定できないため拒否する。
		if (batchDefinitionId == null) {
			return false;
		}

		return batchDefinitionRepository.findById(batchDefinitionId)
				.map(MBatchDefinitionsRecord::getRequiredRole)
				.map(requiredRole -> hasRequiredRole(authentication, requiredRole))
				.orElse(false);
	}

	/**
	 * バッチ実行要求の詳細を現在の認証ユーザーが参照できるか判定する。
	 *
	 * @param authentication 認証情報
	 * @param executionId 外部参照用実行ID
	 * @return 参照可能な場合はtrue
	 */
	public boolean canReadExecution(Authentication authentication, UUID executionId) {

		// 未認証の場合は実行要求の参照を許可しない。
		if (authentication == null || !authentication.isAuthenticated()) {
			return false;
		}

		// 管理者はバッチ定義ごとのrequired_roleに関係なく参照できる。
		if (hasRole(authentication, RoleEnum.ADMIN_ROLE.getStoredName())) {
			return true;
		}

		// 実行要求を特定できない場合は参照権限を判定できないため拒否する。
		if (executionId == null) {
			return false;
		}

		return batchDefinitionRepository.findRequiredRoleByExecutionId(executionId)
				.map(requiredRole -> hasRequiredRole(authentication, requiredRole))
				.orElse(false);
	}

	/**
	 * 認証ユーザーが要求ロール以上のロールを保持しているか判定する。
	 *
	 * @param authentication 認証情報
	 * @param requiredRole バッチ定義に設定された要求ロール
	 * @return 要求ロール以上の場合はtrue
	 */
	private boolean hasRequiredRole(Authentication authentication, String requiredRole) {
		Optional<RoleEnum> required = normalizeRole(requiredRole);

		// 未設定、未知、アクセス禁止ロールは許可条件として使用しない。
		if (required.isEmpty() || required.get() == RoleEnum.BAN_USER_ROLE) {
			return false;
		}

		return authentication.getAuthorities().stream()
				.map(GrantedAuthority::getAuthority)
				.map(this::normalizeRole)
				.flatMap(Optional::stream)
				.filter(role -> role != RoleEnum.BAN_USER_ROLE)
				.max(Comparator.comparingInt(RoleEnum::getValue))
				.map(role -> role.getValue() >= required.get().getValue())
				.orElse(false);
	}

	/**
	 * 認証情報が指定ロールを保持しているか判定する。
	 *
	 * @param authentication 認証情報
	 * @param role DB保存名または正規化済みロール名
	 * @return 指定ロールを保持している場合はtrue
	 */
	private boolean hasRole(Authentication authentication, String role) {

		// 比較対象ロールが未指定の場合は一致不可として扱う。
		if (role == null || role.isBlank()) {
			return false;
		}

		String authority = ROLE_PREFIX + role;
		return authentication.getAuthorities().stream()
				.map(GrantedAuthority::getAuthority)
				.anyMatch(authority::equals);
	}

	/**
	 * ロール表記をSpring Securityの権限比較で利用する保存名へ正規化する。
	 *
	 * @param role DBまたはAPIから取得したロール表記
	 * @return 正規化済みロール。未指定または未知の場合は空
	 */
	private Optional<RoleEnum> normalizeRole(String role) {

		// 未指定のrequired_roleは実行許可に使えないためnullとして扱う。
		if (role == null || role.isBlank()) {
			return Optional.empty();
		}

		String trimmedRole = role.trim();
		String withoutPrefix = trimmedRole.startsWith(ROLE_PREFIX)
				? trimmedRole.substring(ROLE_PREFIX.length())
				: trimmedRole;
		String normalized = withoutPrefix.endsWith("_ROLE")
				? withoutPrefix.substring(0, withoutPrefix.length() - "_ROLE".length())
				: withoutPrefix;
		try {
			return Optional.ofNullable(RoleEnum.from(normalized));
		} catch (IllegalArgumentException _) {

			// 未登録ロールは誤許可を防ぐため拒否対象として扱う。
			return Optional.empty();
		}
	}
}
