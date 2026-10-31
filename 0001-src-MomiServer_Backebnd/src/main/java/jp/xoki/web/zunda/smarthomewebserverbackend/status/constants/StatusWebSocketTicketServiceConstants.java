package jp.xoki.web.zunda.smarthomewebserverbackend.status.constants;

import java.util.Set;

import static jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.RoleEnum.ADMIN_ROLE;
import static jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.RoleEnum.OPERATOR_ROLE;
import static jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.RoleEnum.USER_ROLE;
import static jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.SecurityAuthorityConstants.ROLE_PREFIX;

/** StatusWebSocketTicketServiceで使用する固定値。カテゴリ内の利用元から参照する。 */
public final class StatusWebSocketTicketServiceConstants {

	public static final Set<String> MACHINE_ROLES = Set.of(
			ROLE_PREFIX + ADMIN_ROLE.getStoredName(), ROLE_PREFIX + OPERATOR_ROLE.getStoredName());

	public static final Set<String> SERVICE_ROLES = Set.of(
			ROLE_PREFIX + ADMIN_ROLE.getStoredName(), ROLE_PREFIX + OPERATOR_ROLE.getStoredName(),
			ROLE_PREFIX + USER_ROLE.getStoredName());

	private StatusWebSocketTicketServiceConstants() {
	}
}
