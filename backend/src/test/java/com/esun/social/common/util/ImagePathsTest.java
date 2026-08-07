package com.esun.social.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.esun.social.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ImagePathsTest {

    @Test
    @DisplayName("接受本站上傳端點產生的路徑")
    void acceptsUploadedPath() {
        String path = "/uploads/6f1b0c2e-6d7a-4a1e-9f0d-2b3c4d5e6f70.jpg";

        assertThat(ImagePaths.requireUploadedOrNull(path)).isEqualTo(path);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("沒有圖片時回傳 null")
    void treatsBlankAsNoImage(String path) {
        assertThat(ImagePaths.requireUploadedOrNull(path)).isNull();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "javascript:alert(1)",
                "https://evil.example.com/tracker.png",
                "/uploads/../../etc/passwd",
                "/etc/passwd",
                "/uploads/shell.php",
                "/uploads/note.txt",
                "uploads/no-leading-slash.jpg",
            })
    @DisplayName("外部網址、可執行副檔名與路徑穿越一律拒絕")
    void rejectsAnythingElse(String path) {
        assertThatThrownBy(() -> ImagePaths.requireUploadedOrNull(path)).isInstanceOf(BusinessException.class);
    }
}
