package com.esun.social.presentation.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.esun.social.common.config.SecurityConfig;
import com.esun.social.common.exception.GlobalExceptionHandler;
import com.esun.social.common.security.SecurityErrorWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HealthController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class, SecurityErrorWriter.class})
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/health 回傳統一格式的存活資訊")
    void reportsLiveness() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.application").value("message-me"))
                .andExpect(jsonPath("$.data.timestamp").exists())
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    /**
     * 未列在白名單的路徑一律要求登入，包含根本不存在的路徑。
     *
     * <p>結果是未登入者探測不到哪些端點存在——回應一律是 401，而不是以 404／401 的差異
     * 洩漏 API 的形狀。已登入者對不存在路徑取得的 404 由 {@code GlobalExceptionHandler}
     * 處理，另在整合測試中驗證。
     */
    @Test
    @DisplayName("未登入存取未公開的路徑回傳統一格式的 401，而非容器預設錯誤頁")
    void unknownPathRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/does-not-exist"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
