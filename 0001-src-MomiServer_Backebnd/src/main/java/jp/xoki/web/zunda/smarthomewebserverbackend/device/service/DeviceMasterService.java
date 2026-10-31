package jp.xoki.web.zunda.smarthomewebserverbackend.device.service;

import jp.xoki.web.zunda.jooq.tables.records.MDevicesRecord;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.error.ResourceNotFoundException;
import jp.xoki.web.zunda.smarthomewebserverbackend.device.dto.DeviceMasterDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.device.dto.DeviceMasterRegistRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.device.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** デバイスマスタの取得、登録、更新、DTO変換を行うサービス。 */
@Service
@RequiredArgsConstructor
public class DeviceMasterService {
	private final DeviceRepository deviceRepository;

	/**
	 * 登録されているデバイス一覧を取得する。
	 *
	 * @return デバイス詳細一覧
	 */
	public List<DeviceMasterDetailDto> getAllDevices() {
		return deviceRepository.findAll().stream()
				.map(this::toDetailDto)
				.toList();
	}

	/**
	 * 指定されたIDのデバイス詳細を取得する。
	 *
	 * @param id デバイスID
	 * @return デバイス詳細
	 */
	public DeviceMasterDetailDto getDeviceById(Long id) {
		return deviceRepository.findById(id)
				.map(this::toDetailDto)
				.orElseThrow(() -> new ResourceNotFoundException("device", id));
	}

	/**
	 * リクエストのID有無に応じてデバイスを登録または更新する。
	 *
	 * @param request デバイス登録更新リクエスト
	 * @return 登録または更新後のデバイス詳細
	 */
	public DeviceMasterDetailDto save(DeviceMasterRegistRequest request) {

		// 更新判定
		if (request.getId() != null && request.getId() > 0) {
			int updatedCount = deviceRepository.update(toRecord(request));
			if (updatedCount == 0) {
				throw new ResourceNotFoundException("device", request.getId());
			}
			return getDeviceById(request.getId());
		}

		// 新規作成判定
		Long savedId = deviceRepository.save(toRecord(request));
		return getDeviceById(savedId);
	}

	private DeviceMasterDetailDto toDetailDto(MDevicesRecord databaseRecord) {
		return DeviceMasterDetailDto.builder()
				.id(databaseRecord.getId())
				.devicename(databaseRecord.getDeviceName())
				.address(databaseRecord.getAddress())
				.location(databaseRecord.getLocation())
				.mqttClientId(databaseRecord.getMqttClientId())
				.note(databaseRecord.getNote())
				.status(databaseRecord.getStatus())
				.createdAt(databaseRecord.getCreatedDt())
				.updatedAt(databaseRecord.getUpdatedDt())
				.build();
	}

	private MDevicesRecord toRecord(DeviceMasterRegistRequest request) {
		MDevicesRecord databaseRecord = new MDevicesRecord();
		databaseRecord.setId(request.getId());
		databaseRecord.setDeviceName(request.getDeviceName());
		databaseRecord.setAddress(request.getAddress());
		databaseRecord.setLocation(request.getLocation());
		databaseRecord.setMqttClientId(request.getMqttClientId());
		databaseRecord.setNote(request.getNote());
		databaseRecord.setStatus(request.getStatus());
		return databaseRecord;
	}
}
