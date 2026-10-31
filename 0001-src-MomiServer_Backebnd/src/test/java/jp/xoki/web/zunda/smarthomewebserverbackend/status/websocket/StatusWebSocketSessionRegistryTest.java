package jp.xoki.web.zunda.smarthomewebserverbackend.status.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.xoki.web.zunda.smarthomewebserverbackend.status.dto.StatusDto.Channel;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StatusWebSocketSessionRegistryTest {

	@Test
	void successfulSessionRemainsRegisteredForSubsequentBroadcasts() throws Exception {
		WebSocketSession delegate = mock(WebSocketSession.class);
		when(delegate.getId()).thenReturn("machine-session");
		when(delegate.isOpen()).thenReturn(true);
		StatusWebSocketSessionRegistry registry = new StatusWebSocketSessionRegistry(new ObjectMapper());
		registry.add(Channel.MACHINES, delegate);

		registry.broadcast(Channel.MACHINES, new Message("first"));
		registry.broadcast(Channel.MACHINES, new Message("second"));

		verify(delegate, times(2)).sendMessage(any(TextMessage.class));
	}

	private record Message(String value) {
	}
}
