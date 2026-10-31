package jp.xoki.web.zunda.smarthomewebserverbackend.user.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import jp.xoki.web.zunda.smarthomewebserverbackend.user.constants.UserMessageConstants;

/** 個人設定のテーマをDBとAPIで共通の英語コードへ変換する。 */
public enum UserTheme {
    LIGHT("light"), DARK("dark");

    private final String storedName;

    UserTheme(String storedName) {
        this.storedName = storedName;
    }

    @JsonValue
    public String getStoredName() {
        return storedName;
    }

    @JsonCreator
    public static UserTheme fromStoredName(String value) {
        return Arrays.stream(values()).filter(theme -> theme.storedName.equals(value)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(UserMessageConstants.THEME_INVALID));
    }
}
