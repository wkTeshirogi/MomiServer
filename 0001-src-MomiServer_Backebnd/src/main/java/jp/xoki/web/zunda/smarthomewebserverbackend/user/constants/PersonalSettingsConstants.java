package jp.xoki.web.zunda.smarthomewebserverbackend.user.constants;

/** 個人設定のDTO検証とパスワード処理で共用する入力上限。 */
public final class PersonalSettingsConstants {
    public static final int USERNAME_MAX_LENGTH = 100;
    public static final int PASSWORD_MIN_LENGTH = 8;
    public static final int PASSWORD_MAX_BYTES = 72;

    private PersonalSettingsConstants() {
    }
}
