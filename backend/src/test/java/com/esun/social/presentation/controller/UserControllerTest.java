package com.esun.social.presentation.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.esun.social.business.model.User;
import com.esun.social.business.service.UserService;
import com.esun.social.common.config.SecurityConfig;
import com.esun.social.common.exception.BusinessException;
import com.esun.social.common.exception.ErrorCode;
import com.esun.social.common.exception.GlobalExceptionHandler;
import com.esun.social.common.security.AuthenticatedUser;
import com.esun.social.common.security.SecurityErrorWriter;
import java.time.LocalDateTime;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(UserController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class, SecurityErrorWriter.class})
class UserControllerTest {

    private static final User USER = new User(
            7L,
            "0912345678",
            "王小明",
            "xiaoming@example.com",
            "/uploads/cover.jpg",
            "喜歡咖哩",
            LocalDateTime.of(2026, 1, 1, 9, 0),
            LocalDateTime.of(2026, 1, 2, 9, 0));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    private static RequestPostProcessor loggedInAs(long userId) {
        return authentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(userId, "0912345678"), null, List.of()));
    }

    @Test
    @DisplayName("/me 回傳本人的完整檔案，使用者 ID 取自權杖")
    void returnsCurrentUserProfile() throws Exception {
        when(userService.findById(7L)).thenReturn(USER);

        mockMvc.perform(get("/api/users/me").with(loggedInAs(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(7))
                .andExpect(jsonPath("$.data.phoneNumber").value("0912345678"))
                .andExpect(jsonPath("$.data.email").value("xiaoming@example.com"));
    }

    @Test
    @DisplayName("未登入存取 /me 回 401")
    void requiresAuthenticationForCurrentUser() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("公開檔案不含手機號碼與電子郵件")
    void hidesContactInformationOnPublicProfile() throws Exception {
        when(userService.findById(7L)).thenReturn(USER);

        mockMvc.perform(get("/api/users/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userName").value("王小明"))
                .andExpect(jsonPath("$.data.biography").value("喜歡咖哩"))
                .andExpect(jsonPath("$.data.phoneNumber").doesNotExist())
                .andExpect(jsonPath("$.data.email").doesNotExist())
                .andExpect(content().string(Matchers.not(Matchers.containsString("0912345678"))));
    }

    @Test
    @DisplayName("更新個人檔案時，對象一律是權杖裡的使用者")
    void updatesCurrentUserProfile() throws Exception {
        when(userService.updateProfile(7L, "新名字", null, "新的自我介紹", "/uploads/cover.jpg"))
                .thenReturn(USER);

        mockMvc.perform(put("/api/users/me")
                        .with(loggedInAs(7L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"userName":"新名字","biography":"新的自我介紹","coverImage":"/uploads/cover.jpg"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(7));

        verify(userService).updateProfile(7L, "新名字", null, "新的自我介紹", "/uploads/cover.jpg");
    }

    @Test
    @DisplayName("未登入不能更新個人檔案")
    void requiresAuthenticationToUpdateProfile() throws Exception {
        mockMvc.perform(put("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userName\":\"想改別人的\"}"))
                .andExpect(status().isUnauthorized());

        verify(userService, never()).updateProfile(anyLong(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("使用者名稱為空時回 400")
    void rejectsBlankUserName() throws Exception {
        mockMvc.perform(put("/api/users/me")
                        .with(loggedInAs(7L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userName\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("查無使用者回 404")
    void reportsMissingUser() throws Exception {
        when(userService.findById(anyLong())).thenThrow(new BusinessException(ErrorCode.NOT_FOUND, "找不到這位使用者"));

        mockMvc.perform(get("/api/users/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }
}
