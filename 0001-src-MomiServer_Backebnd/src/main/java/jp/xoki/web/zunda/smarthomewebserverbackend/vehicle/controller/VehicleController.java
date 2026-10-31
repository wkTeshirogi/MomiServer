package jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.security.annotation.AdminOnly;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.security.annotation.CanOperate;
import jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.dto.VehicleDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.dto.VehicleInspectionHistoryDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.dto.VehicleInspectionRenewalRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.dto.VehicleSaveRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.service.VehicleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 車両情報と車検期限変更履歴を提供するController。 */
@RestController
@RequestMapping("/api/vehicles")
@RequiredArgsConstructor
@Tag(name = "VehicleController", description = "車両管理関連のAPI")
public class VehicleController {

	private final VehicleService vehicleService;

	/**
	 * 管理中の車両を一覧取得する。
	 *
	 * @return 車両一覧
	 */
	@GetMapping
	@Operation(summary = "車両一覧取得", operationId = "getVehicleList")
	public ResponseEntity<List<VehicleDetailDto>> list() {
		return ResponseEntity.ok(vehicleService.list());
	}

	/**
	 * 車両IDを指定して車両詳細を取得する。
	 *
	 * @param id 車両ID
	 * @return 車両詳細
	 */
	@GetMapping("/{id}")
	@Operation(summary = "車両詳細取得", operationId = "getVehicleDetail")
	public ResponseEntity<VehicleDetailDto> detail(@PathVariable Long id) {
		return ResponseEntity.ok(vehicleService.detail(id));
	}

	/**
	 * 車両を新規登録する。
	 *
	 * @param request 登録内容
	 * @param authentication 認証情報
	 * @return 登録後の車両詳細
	 */
	@PostMapping
	@CanOperate
	@Operation(summary = "車両新規登録", operationId = "createVehicle")
	public ResponseEntity<VehicleDetailDto> create(@Valid @RequestBody VehicleSaveRequest request,
			Authentication authentication) {
		return ResponseEntity.ok(vehicleService.create(request, authentication.getName()));
	}

	/**
	 * 車両情報を更新する。
	 *
	 * @param id 車両ID
	 * @param request 更新内容
	 * @param authentication 認証情報
	 * @return 更新後の車両詳細
	 */
	@PutMapping("/{id}")
	@CanOperate
	@Operation(summary = "車両更新", operationId = "updateVehicle")
	public ResponseEntity<VehicleDetailDto> update(@PathVariable Long id,
			@Valid @RequestBody VehicleSaveRequest request, Authentication authentication) {
		return ResponseEntity.ok(vehicleService.update(id, request, authentication.getName()));
	}

	/**
	 * 車両を管理対象外へ変更する。
	 *
	 * @param id 車両ID
	 * @param authentication 認証情報
	 * @return 管理終了後の車両詳細
	 */
	@PostMapping("/{id}/archive")
	@AdminOnly
	@Operation(summary = "車両管理終了", operationId = "archiveVehicle")
	public ResponseEntity<VehicleDetailDto> archive(@PathVariable Long id,
			Authentication authentication) {
		return ResponseEntity.ok(vehicleService.archive(id, authentication.getName()));
	}

	/**
	 * 車両の車検期限変更履歴を取得する。
	 *
	 * @param id 車両ID
	 * @return 車検期限変更履歴
	 */
	@GetMapping("/{id}/history")
	@Operation(summary = "車検期限変更履歴取得", operationId = "getVehicleInspectionHistory")
	public ResponseEntity<List<VehicleInspectionHistoryDto>> history(@PathVariable Long id) {
		return ResponseEntity.ok(vehicleService.history(id));
	}

	/**
	 * 車検情報を更新する。
	 *
	 * @param id 車両ID
	 * @param request 車検更新内容
	 * @param authentication 認証情報
	 * @return 更新後の車両詳細
	 */
	@PutMapping("/{id}/inspection")
	@CanOperate
	@Operation(summary = "車検更新", operationId = "renewVehicleInspection")
	public ResponseEntity<VehicleDetailDto> renewInspection(@PathVariable Long id,
			@Valid @RequestBody VehicleInspectionRenewalRequest request, Authentication authentication) {
		return ResponseEntity.ok(vehicleService.renewInspection(id, request, authentication.getName()));
	}
}
