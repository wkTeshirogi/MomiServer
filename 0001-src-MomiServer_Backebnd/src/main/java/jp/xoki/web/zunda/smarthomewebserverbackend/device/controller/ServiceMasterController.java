package jp.xoki.web.zunda.smarthomewebserverbackend.device.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.security.annotation.AdminOnly;

import jp.xoki.web.zunda.smarthomewebserverbackend.device.dto.ServiceMasterDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.device.dto.ServiceMasterRegistRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.device.service.ServiceMasterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** サービスマスタの参照、登録、更新APIを提供する管理者向けコントローラー。 */
@RestController
@AdminOnly
@RequestMapping("/api/master/service")
@RequiredArgsConstructor
@Tag(name = "ServiceMasterController", description = "サービス関連のAPI")
public class ServiceMasterController {
	private final ServiceMasterService serviceMasterService;

	@Operation(
			summary = "サービス一覧取得",
			description = "すべてのサービス情報を返します",
			operationId = "getServiceMasterList")
	@PostMapping("/list")

	/**
	 * 登録されているサービス一覧を取得する。
	 *
	 * @return サービス詳細一覧
	 */
	public ResponseEntity<List<ServiceMasterDetailDto>> list() {
		return ResponseEntity.ok(serviceMasterService.getAllServices());
	}

	@Operation(
			summary = "サービス詳細取得",
			description = "サービスIDを受け取り、サービスの詳細情報を返します",
			operationId = "getServiceMasterDetail")
	@PostMapping("/detail")

	/**
	 * 指定されたIDのサービス詳細を取得する。
	 *
	 * @param id サービスID
	 * @return サービス詳細
	 */
	public ResponseEntity<ServiceMasterDetailDto> detail(Long id) {
		return ResponseEntity.ok(serviceMasterService.getServiceById(id));
	}

	@Operation(
			summary = "サービス登録",
			description = "新しいサービスを登録します",
			operationId = "registerService"
	)
	@PostMapping("/register")

	/**
	 * サービスを登録または更新する。
	 *
	 * @param request サービス登録更新リクエスト
	 * @return 登録または更新後のサービス詳細
	 */
	public ResponseEntity<ServiceMasterDetailDto> register(@Valid @RequestBody ServiceMasterRegistRequest request) {

		// サービス登録の処理
		ServiceMasterDetailDto savedService = serviceMasterService.save(request);
		return ResponseEntity.ok(savedService);
	}
}
