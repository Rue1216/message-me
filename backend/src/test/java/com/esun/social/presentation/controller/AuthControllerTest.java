package com.esun.social.presentation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.esun.social.business.model.AuthToken;
import com.esun.social.business.model.User;
import com.esun.social.business.service.AuthService;
import com.esun.social.common.config.SecurityConfig;
import com.esun.social.common.exception.BusinessException;
import com.esun.social.common.exception.ErrorCode;
import com.esun.social.common.exception.GlobalExceptionHandler;
import com.esun.social.common.security.SecurityErrorWriter;
import java.time.LocalDateTime;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class, SecurityErrorWriter.class})
class AuthControllerTest {

    private static final User CREATED_USER = new User(
            7L,
            "0900000001",
            "小明",
            "a@example.com",
            null,
            null,
            LocalDateTime.of(2026, 1, 1, 9, 0),
            LocalDateTime.of(2026, 1, 1, 9, 0));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    @DisplayName("註冊成功回 201 與新建立的個人檔案")
    void registersSuccessfully() throws Exception {
        when(authService.register("0900000001", "小明", "Test1234!", "a@example.com"))
                .thenReturn(CREATED_USER);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"phoneNumber":"0900000001","userName":"小明",
                                 "password":"Test1234!","email":"a@example.com"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(7))
                .andExpect(jsonPath("$.data.userName").value("小明"))
                .andExpect(jsonPath("$.data.phoneNumber").value("0900000001"))
                // 回應中不得出現任何密碼相關欄位
                .andExpect(content().string(Matchers.not(Matchers.containsString("password"))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("Test1234!"))));
    }

    @ParameterizedTest(name = "{2}")
    @CsvSource({
        "091234567,   Test1234!, 手機號碼位數不足",
        "0812345678,  Test1234!, 手機號碼開頭不是 09",
        "09abcdefgh,  Test1234!, 手機號碼含非數字",
        "0912345678,  短密碼,     密碼長度不足",
    })
    @DisplayName("輸入不合規則時回 400，且不會呼叫業務層")
    void rejectsInvalidRegistration(String phoneNumber, String password, String scenario) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"phoneNumber":"%s","userName":"小明","password":"%s"}
                                """
                                        .formatted(phoneNumber, password)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verify(authService, never()).register(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("手機號碼已被註冊時回 409")
    void reportsDuplicatePhoneNumber() throws Exception {
        when(authService.register(anyString(), anyString(), anyString(), any()))
                .thenThrow(new BusinessException(ErrorCode.PHONE_ALREADY_REGISTERED));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"phoneNumber":"0912345678","userName":"小明","password":"Test1234!"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PHONE_ALREADY_REGISTERED"));
    }

    @Test
    @DisplayName("登入成功回傳權杖與登入者資訊")
    void logsInSuccessfully() throws Exception {
        when(authService.login("0900000001", "Test1234!"))
                .thenReturn(new AuthToken("a.jwt.token", 7200L, CREATED_USER));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"phoneNumber":"0900000001","password":"Test1234!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("a.jwt.token"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(7200))
                .andExpect(jsonPath("$.data.user.userId").value(7));
    }

    @Test
    @DisplayName("憑證錯誤回 401，訊息不透露是帳號還是密碼有問題")
    void rejectsInvalidCredentials() throws Exception {
        when(authService.login(anyString(), anyString()))
                .thenThrow(new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"phoneNumber":"0900000001","password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.error.message").value(ErrorCode.INVALID_CREDENTIALS.defaultMessage()));
    }

    @Test
    @DisplayName("註冊與登入端點不需要既有身分即可存取")
    void endpointsArePubliclyReachable() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\":\"\",\"password\":\"\"}"))
                // 是 400（輸入不合規）而不是 401，代表確實通過了安全過濾鏈
                .andExpect(status().isBadRequest());
    }
}
