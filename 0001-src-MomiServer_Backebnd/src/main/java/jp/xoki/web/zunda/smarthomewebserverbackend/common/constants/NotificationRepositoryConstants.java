package jp.xoki.web.zunda.smarthomewebserverbackend.common.constants;

/** NotificationRepositoryで使用する固定値。カテゴリ内の利用元から参照する。 */
public final class NotificationRepositoryConstants {

	/** JSON補足情報が未指定の場合に保存する空オブジェクト。 */
	public static final String EMPTY_METADATA_JSON = "{}";

	public static final String T_NOTIFICATIONS_NAME = "t_notifications";
	public static final String T_USER_NOTIFICATIONS_NAME = "t_user_notifications";
	public static final String T_NOTIFICATION_ROUTE_SETTINGS_NAME = "t_notification_route_settings";
	public static final String T_USER_NOTIFICATION_PREFERENCES_NAME = "t_user_notification_preferences";
	public static final String M_NOTIFICATION_ROLES_NAME = "m_notification_roles";
	public static final String T_USER_NOTIFICATION_ROLES_NAME = "t_user_notification_roles";
	public static final String NOTIFICATION_ID_VALUE = "notification_id";
	public static final String NOTIFICATION_TYPE_VALUE = "notification_type";
	public static final String USER_ID_VALUE = "user_id";
	public static final String CREATED_DT_VALUE = "created_dt";
	public static final String UPDATED_DT_VALUE = "updated_dt";
	public static final String ENABLED_VALUE = "enabled";


	private NotificationRepositoryConstants() {
	}
}
