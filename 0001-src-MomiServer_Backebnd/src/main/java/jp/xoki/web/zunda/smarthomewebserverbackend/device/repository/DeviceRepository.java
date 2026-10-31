package jp.xoki.web.zunda.smarthomewebserverbackend.device.repository;

import jakarta.validation.constraints.NotNull;
import jp.xoki.web.zunda.jooq.tables.MDevices;
import jp.xoki.web.zunda.jooq.tables.records.MDevicesRecord;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.time.Clock;

/** Repository for device master records. */
@Repository
@RequiredArgsConstructor
public class DeviceRepository {
	private final DSLContext dsl;

	private final Clock clock;

	public @NotNull List<MDevicesRecord> findAll() {
		return dsl.selectFrom(MDevices.M_DEVICES)
				.orderBy(MDevices.M_DEVICES.ID.asc())
				.fetch()
				.into(MDevicesRecord.class);
	}

	public Optional<MDevicesRecord> findById(Long id) {
		return dsl.selectFrom(MDevices.M_DEVICES)
				.where(MDevices.M_DEVICES.ID.eq(id))
				.orderBy(MDevices.M_DEVICES.ID.asc())
				.fetchOptionalInto(MDevicesRecord.class);
	}

	/**
	 * デバイスを登録する。
	 *
	 * @param record 登録するデバイス
	 * @return 登録されたデバイスID
	 */
	public Long save(MDevicesRecord databaseRecord) {
		LocalDateTime now = LocalDateTime.now(clock);
		databaseRecord.setCreatedDt(now);
		databaseRecord.setUpdatedDt(now);

		return dsl.insertInto(MDevices.M_DEVICES)
				.set(MDevices.M_DEVICES.DEVICE_NAME, databaseRecord.getDeviceName())
				.set(MDevices.M_DEVICES.ADDRESS, databaseRecord.getAddress())
				.set(MDevices.M_DEVICES.LOCATION, databaseRecord.getLocation())
				.set(MDevices.M_DEVICES.MQTT_CLIENT_ID, databaseRecord.getMqttClientId())
				.set(MDevices.M_DEVICES.NOTE, databaseRecord.getNote())
				.set(MDevices.M_DEVICES.STATUS, databaseRecord.getStatus())
				.set(MDevices.M_DEVICES.CREATED_DT, databaseRecord.getCreatedDt())
				.set(MDevices.M_DEVICES.UPDATED_DT, databaseRecord.getUpdatedDt())
				.returningResult(MDevices.M_DEVICES.ID)
				.fetchOne(MDevices.M_DEVICES.ID);
	}

	public long save(String deviceName, String address, String location, String mqttClientId, String note, String status) {
		Long id = dsl.insertInto(MDevices.M_DEVICES)
				.set(MDevices.M_DEVICES.DEVICE_NAME, deviceName)
				.set(MDevices.M_DEVICES.ADDRESS, address)
				.set(MDevices.M_DEVICES.LOCATION, location)
				.set(MDevices.M_DEVICES.MQTT_CLIENT_ID, mqttClientId)
				.set(MDevices.M_DEVICES.NOTE, note)
				.set(MDevices.M_DEVICES.STATUS, status)
				.returningResult(MDevices.M_DEVICES.ID)
				.fetchSingle(MDevices.M_DEVICES.ID);
		return java.util.Objects.requireNonNull(id, "Device insert did not return an ID");
	}

	/**
	 * デバイスを更新する。
	 *
	 * @param record 更新するデバイス
	 * @return 更新件数
	 */
	public int update(MDevicesRecord databaseRecord) {
		databaseRecord.setUpdatedDt(LocalDateTime.now(clock));

		return dsl.update(MDevices.M_DEVICES)
				.set(databaseRecord)
				.where(MDevices.M_DEVICES.ID.eq(databaseRecord.getId()))
				.execute();
	}

	public int update(Long id, String deviceName, String address, String location, String mqttClientId, String note,
					  String status) {
		return dsl.update(MDevices.M_DEVICES)
				.set(MDevices.M_DEVICES.DEVICE_NAME, deviceName)
				.set(MDevices.M_DEVICES.ADDRESS, address)
				.set(MDevices.M_DEVICES.LOCATION, location)
				.set(MDevices.M_DEVICES.MQTT_CLIENT_ID, mqttClientId)
				.set(MDevices.M_DEVICES.NOTE, note)
				.set(MDevices.M_DEVICES.STATUS, status)
				.set(MDevices.M_DEVICES.UPDATED_DT, LocalDateTime.now(clock))
				.where(MDevices.M_DEVICES.ID.eq(id))
				.execute();
	}
}
