package jp.xoki.web.zunda.smarthomewebserverbackend.user.service;

import jp.xoki.web.zunda.jooq.tables.records.MUsersRecord;
import jp.xoki.web.zunda.smarthomewebserverbackend.auth.repository.UserRepository;
import jp.xoki.web.zunda.smarthomewebserverbackend.auth.service.AuthSessionService;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.error.ResourceNotFoundException;
import jp.xoki.web.zunda.smarthomewebserverbackend.user.dto.request.PersonalPasswordRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.user.dto.response.PersonalSettingsDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.user.model.UserTheme;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

import static jp.xoki.web.zunda.smarthomewebserverbackend.user.constants.PersonalSettingsConstants.PASSWORD_MAX_BYTES;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.constants.CommonMessageConstants;
import jp.xoki.web.zunda.smarthomewebserverbackend.user.constants.UserMessageConstants;

/** 認証済みの本人だけを対象とする個人設定処理。 */
@Service
@RequiredArgsConstructor
public class PersonalSettingsService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthSessionService sessions;

    /** 本人の個人設定を取得する。
     *
     * @param email 認証情報から取得したメールアドレス
     * @return 表示名とテーマ
     */
    @Transactional(readOnly = true)
    public PersonalSettingsDto get(String email) {
        return loadSettings(email);
    }

    private PersonalSettingsDto loadSettings(String email) {
        return toDto(users.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException(CommonMessageConstants.USER_RESOURCE, email)));
    }

    /** 本人の表示名を保存する。
     *
     * @param email 認証情報から取得したメールアドレス
     * @param username 検証済みの表示名
     * @return 保存後の個人設定
     */
    @Transactional
    public PersonalSettingsDto updateUsername(String email, String username) {
        users.findByEmailForUpdate(email).orElseThrow(() -> new ResourceNotFoundException(CommonMessageConstants.USER_RESOURCE, email));
        users.updatePersonalUsername(email, username.strip());
        return loadSettings(email);
    }

    /** 本人のテーマを保存する。
     *
     * @param email 認証情報から取得したメールアドレス
     * @param theme テーマ
     * @return 保存後の個人設定
     */
    @Transactional
    public PersonalSettingsDto updateTheme(String email, UserTheme theme) {
        users.findByEmailForUpdate(email).orElseThrow(() -> new ResourceNotFoundException(CommonMessageConstants.USER_RESOURCE, email));
        users.updatePersonalTheme(email, theme.getStoredName());
        return loadSettings(email);
    }

    /** 本人のパスワードを変更し、全セッションを失効する。
     *
     * 旧パスワードの照合、ハッシュの更新、全セッション失効は同一トランザクションで行う。
     * @param email 認証情報から取得したメールアドレス
     * @param request 現在・新規・確認用パスワード
     */
    @Transactional
    public void changePassword(String email, PersonalPasswordRequest request) {
        MUsersRecord current = users.findByEmailForUpdate(email)
                .orElseThrow(() -> new ResourceNotFoundException(CommonMessageConstants.USER_RESOURCE, email));
        if (!passwordEncoder.matches(request.getCurrentPassword(), current.getPassword())) {
            throw new IllegalArgumentException(UserMessageConstants.CURRENT_PASSWORD_INCORRECT);
        }
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException(UserMessageConstants.PASSWORD_CONFIRMATION_MISMATCH);
        }

        // BCryptの入力上限は文字数ではなくUTF-8バイト数で判定する。
        if (request.getNewPassword().getBytes(StandardCharsets.UTF_8).length > PASSWORD_MAX_BYTES) {
            throw new IllegalArgumentException(UserMessageConstants.PASSWORD_BYTE_LIMIT_EXCEEDED);
        }
        if (passwordEncoder.matches(request.getNewPassword(), current.getPassword())) {
            throw new IllegalArgumentException(UserMessageConstants.PASSWORD_UNCHANGED);
        }
        users.updatePersonalPassword(email, passwordEncoder.encode(request.getNewPassword()));
        sessions.revokeByUserEmail(email);
    }

    private PersonalSettingsDto toDto(MUsersRecord user) {
        return new PersonalSettingsDto(user.getUsername(), UserTheme.fromStoredName(user.getTheme()));
    }
}
