package jp.xoki.web.zunda.smarthomewebserverbackend.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jp.xoki.web.zunda.smarthomewebserverbackend.user.dto.request.PersonalPasswordRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.user.dto.request.PersonalProfileRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.user.dto.request.PersonalThemeRequest;
import jp.xoki.web.zunda.smarthomewebserverbackend.user.dto.response.PersonalSettingsDto;
import jp.xoki.web.zunda.smarthomewebserverbackend.user.service.PersonalSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 対象のメールアドレスやIDを入力として受け取らず、認証情報の本人だけを更新する。 */
@RestController
@RequestMapping("/api/users/me/settings")
@RequiredArgsConstructor
@Tag(name = "PersonalSettings", description = "本人の個人設定")
public class PersonalSettingsController {
    private final PersonalSettingsService service;

    /** 本人の設定を取得する。
     *
     * @param authentication 認証情報
     * @return 本人の表示名とテーマ
     */
    @GetMapping
    @Operation(summary = "個人設定取得", operationId = "getPersonalSettings")
    public PersonalSettingsDto get(Authentication authentication) {
        return service.get(authentication.getName());
    }

    /** 本人の表示名を変更する。
     *
     * @param authentication 認証情報
     * @param request 表示名
     * @return 更新後の設定
     */
    @PutMapping("/profile")
    @Operation(summary = "本人のユーザー名変更", operationId = "updatePersonalUsername")
    public PersonalSettingsDto updateProfile(Authentication authentication,
            @Valid @RequestBody PersonalProfileRequest request) {
        return service.updateUsername(authentication.getName(), request.username());
    }

    /** 本人のテーマを変更する。
     *
     * @param authentication 認証情報
     * @param request テーマ
     * @return 更新後の設定
     */
    @PutMapping("/theme")
    @Operation(summary = "本人のテーマ変更", operationId = "updatePersonalTheme")
    public PersonalSettingsDto updateTheme(Authentication authentication,
            @Valid @RequestBody PersonalThemeRequest request) {
        return service.updateTheme(authentication.getName(), request.theme());
    }

    /** 本人のパスワードを変更し、全セッションを失効する。
     *
     * @param authentication 認証情報
     * @param request 現在・新規・確認用パスワード
     * @return 内容を持たない成功応答
     */
    @PostMapping("/password")
    @Operation(summary = "本人のパスワード変更", operationId = "changePersonalPassword")
    public ResponseEntity<Void> changePassword(Authentication authentication,
            @Valid @RequestBody PersonalPasswordRequest request) {
        service.changePassword(authentication.getName(), request);
        return ResponseEntity.noContent().build();
    }
}
