package jp.xoki.web.zunda.smarthomewebserverbackend.status.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import jp.xoki.web.zunda.smarthomewebserverbackend.status.dto.StatusDto.Channel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import static jp.xoki.web.zunda.smarthomewebserverbackend.status.constants.StatusWebSocketSessionRegistryConstants.BUFFER_SIZE_LIMIT_BYTES;
import static jp.xoki.web.zunda.smarthomewebserverbackend.status.constants.StatusWebSocketSessionRegistryConstants.SEND_TIME_LIMIT_MILLIS;

/** 認証済みWebSocketセッションを用途別に管理し、JSONメッセージを配信する。 */
@Component
@RequiredArgsConstructor
public class StatusWebSocketSessionRegistry {

	private final ObjectMapper objectMapper;
	private final Map<Channel, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

	public WebSocketSession add(Channel channel, WebSocketSession session) {

		// 定期配信等が同時送信してもセッションへの書込みを直列化し、遅い受信側の送信時間・バッファを制限する。
		WebSocketSession concurrent = new ConcurrentWebSocketSessionDecorator(
				session, SEND_TIME_LIMIT_MILLIS, BUFFER_SIZE_LIMIT_BYTES);
		sessions.computeIfAbsent(channel, ignored -> ConcurrentHashMap.newKeySet()).add(concurrent);
		return concurrent;
	}

	public void remove(Channel channel, String sessionId) {
		Set<WebSocketSession> channelSessions = sessions.get(channel);
		if (channelSessions != null) {
			channelSessions.removeIf(session -> session.getId().equals(sessionId));
		}
	}

	public void send(WebSocketSession session, Object payload) {
		try {
			session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
		} catch (IOException | IllegalStateException _) {
			closeQuietly(session);
		}
	}

	public void broadcast(Channel channel, Object payload) {
		Set<WebSocketSession> channelSessions = sessions.get(channel);
		if (channelSessions == null || channelSessions.isEmpty()) {
			return;
		}
		String json;
		try {
			json = objectMapper.writeValueAsString(payload);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("Failed to serialize status message", exception);
		}
		TextMessage message = new TextMessage(json);

		// 切断・送信失敗は接続単位で除外し、他ユーザーへの同じスナップショット配信を継続する。
		channelSessions.removeIf(session -> sendFailed(session, message));
	}

	private boolean sendFailed(WebSocketSession session, TextMessage message) {
		if (!session.isOpen()) {
			return true;
		}
		try {
			session.sendMessage(message);
			return false;
		} catch (IOException | IllegalStateException _) {
			closeQuietly(session);
			return true;
		}
	}

	private void closeQuietly(WebSocketSession session) {
		try {
			if (session.isOpen()) {
				session.close(CloseStatus.SERVER_ERROR);
			}
		} catch (IOException _) {
			// 切断済みセッションの後処理では追加対応を行わない。
		}
	}
}
