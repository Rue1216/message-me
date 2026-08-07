package com.esun.social.common.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.esun.social.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

/**
 * 驗證統一回應格式的序列化結果。
 *
 * <p>以 {@link JsonTest} 載入正式的 Jackson 設定（application.yml 的
 * {@code default-property-inclusion: non_null}），確保測到的是實際上線的序列化行為，
 * 而非測試中自行 new 出來的 ObjectMapper。
 */
@JsonTest
class ApiResponseTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Nested
    @DisplayName("成功回應")
    class Success {

        @Test
        @DisplayName("包含 data，且不輸出 error 欄位")
        void serialisesDataWithoutErrorField() throws Exception {
            ApiResponse<Map<String, String>> response = ApiResponse.success(Map.of("id", "1"));

            String json = objectMapper.writeValueAsString(response);

            assertThat(json).contains("\"success\":true").contains("\"id\":\"1\"");
            assertThat(json).doesNotContain("error");
        }

        @Test
        @DisplayName("沒有回傳內容時 data 也一併省略")
        void serialisesEmptyPayload() throws Exception {
            String json = objectMapper.writeValueAsString(ApiResponse.noContent());

            assertThat(json).isEqualTo("{\"success\":true}");
        }
    }

    @Nested
    @DisplayName("失敗回應")
    class Failure {

        @Test
        @DisplayName("以 ErrorCode 名稱作為錯誤代碼，且不輸出 data 欄位")
        void serialisesErrorWithoutDataField() throws Exception {
            ApiResponse<Void> response = ApiResponse.failure(ErrorCode.NOT_FOUND, "找不到這篇發文");

            String json = objectMapper.writeValueAsString(response);

            assertThat(json)
                    .contains("\"success\":false")
                    .contains("\"code\":\"NOT_FOUND\"")
                    .contains("\"message\":\"找不到這篇發文\"");
            assertThat(json).doesNotContain("\"data\"");
        }

        @Test
        @DisplayName("未指定訊息時採用 ErrorCode 的預設訊息")
        void fallsBackToDefaultMessage() {
            ApiResponse<Void> response = ApiResponse.failure(ErrorCode.UNAUTHORIZED, null);

            assertThat(response.success()).isFalse();
            assertThat(response.error().message()).isEqualTo(ErrorCode.UNAUTHORIZED.defaultMessage());
        }
    }
}
