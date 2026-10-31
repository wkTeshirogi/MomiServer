package jp.xoki.web.zunda.smarthomewebserverbackend.status.websocket;

import jp.xoki.web.zunda.smarthomewebserverbackend.common.config.AppCorsProperties;
import jp.xoki.web.zunda.smarthomewebserverbackend.status.dto.StatusDto.Channel;
import jp.xoki.web.zunda.smarthomewebserverbackend.status.service.StatusSnapshotService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** マシン・サービス状態配信WebSocketのエンドポイントを登録する。 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class StatusWebSocketConfig implements WebSocketConfigurer {

	private final StatusSnapshotService snapshots;
	private final StatusWebSocketSessionRegistry sessions;
	private final StatusTicketHandshakeInterceptor tickets;
	private final AppCorsProperties cors;

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		String[] allowedOrigins = cors.getAllowedOrigins().toArray(String[]::new);
		registry.addHandler(new StatusWebSocketHandler(Channel.MACHINES, snapshots, sessions), Channel.MACHINES.path())
				.addInterceptors(tickets)
				.setAllowedOrigins(allowedOrigins);
		registry.addHandler(new StatusWebSocketHandler(Channel.SERVICES, snapshots, sessions), Channel.SERVICES.path())
				.addInterceptors(tickets)
				.setAllowedOrigins(allowedOrigins);
	}
}
