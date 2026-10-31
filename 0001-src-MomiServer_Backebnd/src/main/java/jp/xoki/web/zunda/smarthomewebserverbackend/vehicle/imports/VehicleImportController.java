package jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.imports;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.security.annotation.CanOperate;
import jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.dto.VehicleDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.dto.VehicleSaveRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

/** 車両管理内のPDF・画像取込確認API。 */
@RestController
@RequestMapping("/api/vehicle-imports")
@CanOperate
public class VehicleImportController {
	private final VehicleImportService service;
	public VehicleImportController(VehicleImportService service) { this.service = service; }

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@Operation(summary = "車検証ファイル取込", operationId = "uploadVehicleImport")
	public ResponseEntity<VehicleImportDetailDto> upload(@RequestPart("file") MultipartFile file, Authentication authentication) {
		return ResponseEntity.ok(service.upload(file, authentication.getName()));
	}

	@GetMapping("/{importId}")
	@Operation(summary = "車両取込確認情報取得", operationId = "getVehicleImport")
	public ResponseEntity<VehicleImportDetailDto> get(@PathVariable UUID importId) { return ResponseEntity.ok(service.get(importId)); }

	@GetMapping("/{importId}/source")
	@Operation(summary = "車両取込原本取得", operationId = "getVehicleImportSource")
	public ResponseEntity<byte[]> source(@PathVariable UUID importId) throws IOException {
		VehicleImportService.SourceDocument source = service.source(importId);
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType(source.mediaType())).body(source.bytes());
	}

	@PostMapping("/{importId}/confirm")
	@Operation(summary = "車両取込確認登録", operationId = "confirmVehicleImport")
	public ResponseEntity<VehicleDetailDto> confirm(@PathVariable UUID importId, @Valid @RequestBody VehicleSaveRequest request,
			Authentication authentication) { return ResponseEntity.ok(service.confirm(importId, request, authentication.getName())); }

	@PostMapping("/{importId}/cancel")
	@Operation(summary = "車両取込取消", operationId = "cancelVehicleImport")
	public ResponseEntity<Void> cancel(@PathVariable UUID importId) { service.cancel(importId); return ResponseEntity.noContent().build(); }
}
