package jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.imports;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** 車両取込の一時保存および保持期間に関する設定。 */
@Getter
@Setter
@Component
@ConfigurationProperties("app.vehicle-import")
public class VehicleImportProperties {
	private String root = System.getProperty("java.io.tmpdir") + "/smarthome-vehicle-imports";
	private long maxBytes = 10L * 1024 * 1024;
	private long maxImagePixels = 40_000_000;
	private int maxPdfPages = 10;
	private Duration reviewLifetime = Duration.ofHours(24);
	private String cleanupCron = "0 15 * * * *";
}
