package jp.xoki.web.zunda.smarthomewebserverbackend.scheduling.scheduler;

import jp.xoki.web.zunda.smarthomewebserverbackend.status.websocket.StatusWebSocketPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** ステータス画面へのWebSocket配信を開始する定期実行入口。 */
@Component
@RequiredArgsConstructor
public class StatusWebSocketBroadcastScheduler {

	private final StatusWebSocketPublisher publisher;

	/** 最新状態の定期配信。
	 *
	 * 初回待機・実行間隔は従来と同じ設定を使用し、配信処理はPublisherに維持する。
	 */
	@Scheduled(
			fixedDelayString = "${app.status.websocket.broadcast-interval-ms:5000}",
			initialDelayString = "${app.status.websocket.broadcast-interval-ms:5000}"
	)
	public void publish() {
		publisher.publish();
	}
}
