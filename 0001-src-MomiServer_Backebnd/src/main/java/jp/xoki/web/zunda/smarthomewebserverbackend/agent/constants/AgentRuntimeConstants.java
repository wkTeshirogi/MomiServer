package jp.xoki.web.zunda.smarthomewebserverbackend.agent.constants;

/** Agent管理と状態表示が共用する稼働判定の契約。 */
public final class AgentRuntimeConstants {

	/** ハートビートからオフラインと判定するまでの分数。 */
	public static final int HEARTBEAT_OFFLINE_MINUTES = 3;

	private AgentRuntimeConstants() {
	}
}
