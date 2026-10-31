package jp.xoki.web.zunda.smarthomewebserverbackend.status.websocket;

import jp.xoki.web.zunda.smarthomewebserverbackend.status.dto.StatusDto.Channel;
import jp.xoki.web.zunda.smarthomewebserverbackend.status.service.StatusWebSocketTicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/** 一回限りの接続チケットを検証してWebSocketセッションへ認証情報を引き継ぐ。 */
@Component
@RequiredArgsConstructor
public class StatusTicketHandshakeInterceptor implements HandshakeInterceptor {

	private final StatusWebSocketTicketService tickets;

	@Override
	public boolean beforeHandshake(
			ServerHttpRequest request,
			ServerHttpResponse response,
			WebSocketHandler wsHandler,
			Map<String, Object> attributes
	) {
		String path = request.getURI().getPath();
		Channel channel = path.endsWith(Channel.MACHINES.path()) ? Channel.MACHINES : Channel.SERVICES;
		String ticketValue = UriComponentsBuilder.fromUri(request.getURI())
				.build().getQueryParams().getFirst("ticket");
		try {
			StatusWebSocketTicketService.Ticket ticket = tickets.consume(ticketValue, channel);
			attributes.put("username", ticket.username());
			attributes.put("roles", ticket.roles());
			attributes.put("channel", channel.value());
			return true;
		} catch (AccessDeniedException _) {
			response.setStatusCode(HttpStatus.UNAUTHORIZED);
			return false;
		}
	}

	@Override
	public void afterHandshake(
			ServerHttpRequest request,
			ServerHttpResponse response,
			WebSocketHandler wsHandler,
			Exception exception
	) {
		// ハンドシェイク後に追加処理は行わない。
	}
}
