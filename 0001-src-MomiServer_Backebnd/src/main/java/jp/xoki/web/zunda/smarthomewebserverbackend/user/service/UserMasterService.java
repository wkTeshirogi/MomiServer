package jp.xoki.web.zunda.smarthomewebserverbackend.user.service;

import jp.xoki.web.zunda.smarthomewebserverbackend.auth.repository.UserRepository;
import jp.xoki.web.zunda.smarthomewebserverbackend.user.dto.response.UserMasterDetailDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.error.ResourceNotFoundException;

import java.util.List;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.CommonMessageConstants;

/** ユーザーマスタの取得とDTO変換を行うサービス。 */
@Service
@RequiredArgsConstructor
public class UserMasterService {
	private final UserRepository userRepository;

	/**
	 * 登録されているユーザー一覧を取得する。
	 *
	 * @return ユーザー詳細一覧
	 */
	public List<UserMasterDetailDto> getUserMasterList() {
		return userRepository.findAll().stream().map(databaseRecord -> UserMasterDetailDto.builder()
					.id(databaseRecord.getId())
					.email(databaseRecord.getEmail())
					.role(databaseRecord.getRole())
					.createdDt(databaseRecord.getCreatedDt())
					.updatedDt(databaseRecord.getUpdatedDt())
					.lastLoginAt(databaseRecord.getLastloginat())
					.build()).toList();
	}

	/**
	 * 指定されたIDのユーザー詳細を取得する。
	 *
	 * @param id ユーザーID
	 * @return ユーザー詳細
	 */
	public UserMasterDetailDto getUserMaster(Long id) {
		return userRepository.findById(id).map(databaseRecord -> UserMasterDetailDto.builder()
					.id(databaseRecord.getId())
					.email(databaseRecord.getEmail())
					.role(databaseRecord.getRole())
					.createdDt(databaseRecord.getCreatedDt())
					.updatedDt(databaseRecord.getUpdatedDt())
					.lastLoginAt(databaseRecord.getLastloginat())
					.build()).orElseThrow(() -> new ResourceNotFoundException(CommonMessageConstants.USER_RESOURCE, id));
	}

	/** ユーザー保存処理の拡張用メソッド。現時点では未実装。 */
	public void save(){
		// User changes are handled by the existing authentication and user-management services.
	}
}
