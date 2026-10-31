package jp.xoki.web.zunda.smarthomewebserverbackend.common.service;

import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.BatchGlobalSettingDetailDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.dto.BatchGlobalSettingSaveRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.repository.BatchGlobalSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** バッチ共通設定の取得・保存を提供するService。 */
@Service
@RequiredArgsConstructor
public class BatchGlobalSettingService {

	private final BatchGlobalSettingRepository repository;

	public BatchGlobalSettingDetailDto detail() {
		return repository.find().orElseThrow(
				() -> new IllegalStateException("Batch global settings have not been initialized"));
	}

	@Transactional
	public BatchGlobalSettingDetailDto save(BatchGlobalSettingSaveRequest request) {
		return repository.save(request);
	}
}
