package jp.xoki.web.zunda.smarthomewebserverbackend.device.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.security.annotation.AdminOnly;
import jp.xoki.web.zunda.smarthomewebserverbackend.device.dto.DeviceMasterDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.device.dto.DeviceMasterRegistRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.device.service.DeviceMasterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** デバイスマスタの参照APIを提供する管理者向けコントローラー。 */
@RestController
@AdminOnly
@RequestMapping("/api/master/device")
@RequiredArgsConstructor
@Tag(name = "DeviceMasterController", description = "デバイス関連のAPI")
public class DeviceMasterController {
	private final DeviceMasterService deviceMasterService;

	/**
	 * デバイスの詳細取得
	 *
	 * @param id
	 * @return
	 */
	@Operation(
			summary = "デバイス詳細取得",
			description = "デバイスIDを受け取り、デバイスの詳細情報を返します",
			operationId = "getDeviceMasterDetail")
	@PostMapping("/detail")
	public ResponseEntity<DeviceMasterDetailDto> detail(Long id) {
		return ResponseEntity.ok(deviceMasterService.getDeviceById(id));
	}

	/**
	 * デバイス一覧取得
	 * @return
	 */
	@Operation(
			summary = "デバイス一覧取得",
			description = "すべてのデバイス情報を返します",
			operationId = "getDeviceMasterList")
	@PostMapping("/list")

	/**
	 * 登録されているデバイス一覧を取得する。
	 *
	 * @return デバイス詳細一覧
	 */
	public ResponseEntity<List<DeviceMasterDetailDto>> list() {
		return ResponseEntity.ok(deviceMasterService.getAllDevices());
	}

	@Operation(
			summary = "デバイス登録",
			description = "新しいデバイスを登録します",
			operationId = "registerDevice")
	@PostMapping("/register")
	public ResponseEntity<DeviceMasterDetailDto> register(@Valid @RequestBody DeviceMasterRegistRequest request) {
		return ResponseEntity.ok(deviceMasterService.save(request));
	}
}
