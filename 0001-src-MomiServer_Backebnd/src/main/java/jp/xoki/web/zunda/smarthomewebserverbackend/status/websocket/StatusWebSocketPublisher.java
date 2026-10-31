package jp.xoki.web.zunda.smarthomewebserverbackend.status.websocket;

import jp.xoki.web.zunda.smarthomewebserverbackend.status.dto.StatusDto.Channel;
import jp.xoki.web.zunda.smarthomewebserverbackend.status.service.StatusSnapshotService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 接続中の画面へ最新状態の全件スナップショットを定期配信する。 */
@Component
@RequiredArgsConstructor
public class StatusWebSocketPublisher {

	private final StatusSnapshotService snapshots;
	private final StatusWebSocketSessionRegistry sessions;

	/** 最新状態の配信。
	 *
	 * Schedulerから呼び出し、既存のセッション管理を通じて閲覧可能な状態を配信する。
	 */
	public void publish() {
		sessions.broadcast(Channel.MACHINES, snapshots.machineMessage());
		sessions.broadcast(Channel.SERVICES, snapshots.serviceMessage());
	}
}
