package com.esun.social.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.esun.social.common.exception.BusinessException;
import com.esun.social.common.exception.ErrorCode;
import java.time.LocalDateTime;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CursorTest {

    private static final LocalDateTime TIME = LocalDateTime.of(2026, 1, 2, 3, 4, 5);

    @Test
    @DisplayName("編碼後再解碼可還原原本的位置")
    void roundTrips() {
        Cursor.Position decoded = Cursor.decode(Cursor.encode(TIME, 42L));

        assertThat(decoded.createdAt()).isEqualTo(TIME);
        assertThat(decoded.id()).isEqualTo(42L);
    }

    @Test
    @DisplayName("帶有奈秒的時間也能完整還原")
    void preservesSubSecondPrecision() {
        LocalDateTime precise = LocalDateTime.of(2026, 1, 2, 3, 4, 5, 123_000_000);

        assertThat(Cursor.decode(Cursor.encode(precise, 1L)).createdAt()).isEqualTo(precise);
    }

    @Test
    @DisplayName("編碼結果不透露內部結構，且可安全放進網址")
    void encodesToUrlSafeOpaqueString() {
        String encoded = Cursor.encode(TIME, 42L);

        assertThat(encoded).doesNotContain("|").doesNotContain("2026").doesNotContain("+", "/", "=");
    }

    @Test
    @DisplayName("空游標代表第一頁")
    void treatsBlankAsFirstPage() {
        assertThat(Cursor.decode(null)).isNull();
        assertThat(Cursor.decode("")).isNull();
        assertThat(Cursor.decode("   ")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"!!!not-base64!!!", "bm90LWEtY3Vyc29y", "MjAyNi0wMS0wMlQwMzowNDowNXxub3QtYS1udW1iZXI"})
    @DisplayName("格式錯誤的游標視為輸入錯誤（400），而不是伺服器錯誤")
    void rejectsMalformedCursor(String malformed) {
        assertThatThrownBy(() -> Cursor.decode(malformed))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(InstanceOfAssertFactories.type(BusinessException.class))
                .extracting(BusinessException::errorCode)
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }
}
