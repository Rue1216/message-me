package com.esun.social.common.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 驗證所有例外都被收斂為統一的 {@code ApiResponse} 失敗格式，
 * 且對外不洩漏內部細節（stack trace、原始例外訊息）。
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new StubController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("BusinessException 依 ErrorCode 對應狀態碼與錯誤代碼")
    void mapsBusinessException() throws Exception {
        mockMvc.perform(get("/stub/business"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("PHONE_ALREADY_REGISTERED"))
                .andExpect(jsonPath("$.error.message").value("此手機號碼已經註冊過了"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("Bean Validation 失敗回 400，訊息帶出欄位名稱")
    void mapsValidationFailure() throws Exception {
        mockMvc.perform(post("/stub/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").value(Matchers.containsString("content")))
                .andExpect(jsonPath("$.error.message").value(Matchers.containsString("內容不可為空")));
    }

    @Test
    @DisplayName("JSON 格式錯誤回 400，不回傳解析器的原始訊息")
    void mapsMalformedJson() throws Exception {
        mockMvc.perform(post("/stub/validated").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MALFORMED_REQUEST"))
                .andExpect(content().string(Matchers.not(Matchers.containsString("JsonParseException"))));
    }

    @Test
    @DisplayName("路徑參數型別不符回 400")
    void mapsTypeMismatch() throws Exception {
        mockMvc.perform(get("/stub/posts/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("HTTP 方法不支援回 405")
    void mapsMethodNotSupported() throws Exception {
        mockMvc.perform(post("/stub/business"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("未預期的例外回 500，且不洩漏原始例外訊息與 stack trace")
    void hidesUnexpectedExceptionDetails() throws Exception {
        mockMvc.perform(get("/stub/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error.message").value(ErrorCode.INTERNAL_ERROR.defaultMessage()))
                .andExpect(content().string(Matchers.not(Matchers.containsString("資料庫密碼"))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("IllegalStateException"))));
    }

    @RestController
    @RequestMapping("/stub")
    static class StubController {

        @GetMapping("/business")
        void business() {
            throw new BusinessException(ErrorCode.PHONE_ALREADY_REGISTERED);
        }

        @PostMapping("/validated")
        void validated(@Valid @RequestBody Payload payload) {
            // 僅供觸發驗證流程
        }

        @GetMapping("/posts/{postId}")
        void byId(@PathVariable long postId) {
            // 僅供觸發型別轉換錯誤
        }

        @GetMapping("/boom")
        void boom() {
            throw new IllegalStateException("資料庫密碼是 hunter2");
        }

        record Payload(@NotBlank(message = "內容不可為空") String content) {}
    }
}
