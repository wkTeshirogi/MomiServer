package jp.xoki.web.zunda.smarthomewebserverbackend.status.websocket;

import jp.xoki.web.zunda.smarthomewebserverbackend.status.dto.StatusDto.Channel;
import jp.xoki.web.zunda.smarthomewebserverbackend.status.service.StatusSnapshotService;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/** 読み取り専用の状態配信WebSocketハンドラー。 */
public class StatusWebSocketHandler extends TextWebSocketHandler {

	private final Channel channel;
	private final StatusSnapshotService snapshots;
	private final StatusWebSocketSessionRegistry sessions;

	public StatusWebSocketHandler(
			Channel channel,
			StatusSnapshotService snapshots,
			StatusWebSocketSessionRegistry sessions
	) {
		this.channel = channel;
		this.snapshots = snapshots;
		this.sessions = sessions;
	}

	@Override
	public void afterConnectionEstablished(WebSocketSession session) {
		WebSocketSession concurrent = sessions.add(channel, session);
		Object initial = channel == Channel.MACHINES
				? snapshots.machineMessage() : snapshots.serviceMessage();
		sessions.send(concurrent, initial);
	}

	@Override
	protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
		session.close(CloseStatus.POLICY_VIOLATION.withReason("Read-only status stream"));
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
		sessions.remove(channel, session.getId());
	}

	@Override
	public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
		sessions.remove(channel, session.getId());
		if (session.isOpen()) {
			session.close(CloseStatus.SERVER_ERROR);
		}
	}
}
