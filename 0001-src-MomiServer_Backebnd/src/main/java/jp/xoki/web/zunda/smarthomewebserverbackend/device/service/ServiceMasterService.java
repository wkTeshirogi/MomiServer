package jp.xoki.web.zunda.smarthomewebserverbackend.device.service;

import jp.xoki.web.zunda.smarthomewebserverbackend.common.error.ResourceNotFoundException;
import jp.xoki.web.zunda.smarthomewebserverbackend.device.dto.ServiceMasterDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.device.dto.ServiceMasterRegistRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.device.repository.ServiceRepository;
import jp.xoki.web.zunda.jooq.tables.records.MServicesRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** サービスマスタの取得、登録、更新、DTO変換を行うサービス。 */
@Service
@RequiredArgsConstructor
public class ServiceMasterService {
	private final ServiceRepository serviceRepository;

	/**
	 * 登録されているサービス一覧を取得する。
	 *
	 * @return サービス詳細一覧
	 */
	public List<ServiceMasterDetailDto> getAllServices() {
		return serviceRepository.findAll().stream()
				.map(this::toDetailDto)
				.toList();
	}

	/**
	 * 指定されたIDのサービス詳細を取得する。
	 *
	 * @param id サービスID
	 * @return サービス詳細
	 */
	public ServiceMasterDetailDto getServiceById(Long id) {
		return serviceRepository.findById(id)
				.map(this::toDetailDto)
				.orElseThrow(() -> new ResourceNotFoundException("service", id));
	}

	/**
	 * リクエストのID有無に応じてサービスを登録または更新する。
	 *
	 * @param request サービス登録更新リクエスト
	 * @return 登録または更新後のサービス詳細
	 */
	public ServiceMasterDetailDto save(ServiceMasterRegistRequest request) {
		String noticeLevel = request.getNoticeLevel() == null ? "none" : request.getNoticeLevel();
		if (request.getId() != null && request.getId() > 0) {
			int updatedCount = serviceRepository.update(toRecord(request, noticeLevel));
			if (updatedCount == 0) {
				throw new ResourceNotFoundException("service", request.getId());
			}
			return getServiceById(request.getId());
		}

		Long savedId = serviceRepository.save(toRecord(request, noticeLevel));
		return getServiceById(savedId);
	}

	/**
	 * jOOQのサービスレコードをAPIレスポンス用DTOへ変換する。
	 *
	 * @param record サービスレコード
	 * @return サービス詳細DTO
	 */
	private ServiceMasterDetailDto toDetailDto(MServicesRecord databaseRecord) {
		return ServiceMasterDetailDto.builder()
				.id(databaseRecord.getId())
				.serviceName(databaseRecord.getServiceName())
				.viewName(databaseRecord.getViewName())
				.targetMachine(databaseRecord.getTargetMachine())
				.status(databaseRecord.getStatus())
				.createdAt(databaseRecord.getCreatedDt())
				.updatedAt(databaseRecord.getUpdatedDt())
				.allowStart(databaseRecord.getAllowStart())
				.description(databaseRecord.getDescription())
				.allowStop(databaseRecord.getAllowStop())
				.allowRestart(databaseRecord.getAllowRestart())
				.autoRestart(databaseRecord.getAutoRestart())
				.noticeLevel(databaseRecord.getNoticeLevel())
				.logPath(databaseRecord.getLogPath())
				.useJournalctl(databaseRecord.getUseJournalctl())
				.build();
	}

	private MServicesRecord toRecord(ServiceMasterRegistRequest request, String noticeLevel) {
		MServicesRecord databaseRecord = new MServicesRecord();
		databaseRecord.setId(request.getId());
		databaseRecord.setServiceName(request.getServiceName());
		databaseRecord.setViewName(request.getViewName());
		databaseRecord.setTargetMachine(request.getTargetMachine());
		databaseRecord.setStatus(request.getStatus());
		databaseRecord.setDescription(request.getDescription());
		databaseRecord.setAllowStart(request.isAllowStart());
		databaseRecord.setAllowStop(request.isAllowStop());
		databaseRecord.setAllowRestart(request.isAllowRestart());
		databaseRecord.setAutoRestart(request.isAutoRestart());
		databaseRecord.setNoticeLevel(noticeLevel);
		databaseRecord.setLogPath(request.getLogPath());
		databaseRecord.setUseJournalctl(request.isUseJournalctl());
		return databaseRecord;
	}
}
