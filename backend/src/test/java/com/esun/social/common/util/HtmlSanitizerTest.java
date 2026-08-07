package com.esun.social.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HtmlSanitizerTest {

    private final HtmlSanitizer sanitizer = new HtmlSanitizer();

    @ParameterizedTest
    @ValueSource(
            strings = {
                "<script>alert(1)</script>",
                "<img src=x onerror=alert(1)>",
                "<iframe src=\"javascript:alert(1)\"></iframe>",
                "<svg/onload=alert(1)>",
                "<a href=\"javascript:alert(1)\"></a>",
            })
    @DisplayName("常見的 XSS payload 不會留下任何可執行內容")
    void stripsXssPayloads(String payload) {
        assertThat(sanitizer.sanitize(payload)).doesNotContain("<", ">", "alert(1)");
    }

    @Test
    @DisplayName("移除標籤但保留其中的文字")
    void keepsTextInsideTags() {
        assertThat(sanitizer.sanitize("<b>粗體</b>字")).isEqualTo("粗體字");
    }

    @Test
    @DisplayName("純文字原樣保留，不做 HTML 實體編碼")
    void leavesPlainTextIntact() {
        assertThat(sanitizer.sanitize("咖哩 & 白飯 < 3")).isEqualTo("咖哩 & 白飯 < 3");
        assertThat(sanitizer.sanitize("SQL 注入測試：' OR '1'='1")).isEqualTo("SQL 注入測試：' OR '1'='1");
    }

    @Test
    @DisplayName("保留換行，讓多行發文不被壓成一行")
    void preservesLineBreaks() {
        assertThat(sanitizer.sanitize("第一行\n第二行")).isEqualTo("第一行\n第二行");
    }

    @Test
    @DisplayName("去除前後空白；null 維持 null")
    void trimsAndPassesThroughNull() {
        assertThat(sanitizer.sanitize("  內容  ")).isEqualTo("內容");
        assertThat(sanitizer.sanitize(null)).isNull();
    }
}
