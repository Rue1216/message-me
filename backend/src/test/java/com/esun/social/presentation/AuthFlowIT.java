package com.esun.social.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.esun.social.support.MySqlContainerSupport;
import com.esun.social.support.TestData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 註冊 → 登入 → 存取受保護資源的端到端驗證，跑在真實資料庫與完整的過濾鏈上。
 *
 * <p>單元測試裡每一層的相鄰層都是假的；這裡驗證的是把它們接起來之後真的能動——
 * 特別是密碼雜湊與資料庫既有資料的相容性，那是 mock 永遠測不到的部分。
 */
@AutoConfigureMockMvc
class AuthFlowIT extends MySqlContainerSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("註冊後可用同一組憑證登入，並以取得的權杖存取 /api/users/me")
    void registerThenLoginThenAccessProtectedResource() throws Exception {
        String phoneNumber = TestData.uniquePhoneNumber();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"phoneNumber":"%s","userName":"整合測試使用者",
                                 "password":"Test1234!","email":"it@example.com"}
                                """
                                        .formatted(phoneNumber)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.userName").value("整合測試使用者"));

        String token = login(phoneNumber, "Test1234!");

        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phoneNumber").value(phoneNumber))
                .andExpect(jsonPath("$.data.userName").value("整合測試使用者"));
    }

    @Test
    @DisplayName("種子資料的示範帳號可以登入 —— 應用層的雜湊與 DB 的密碼契約一致")
    void seededAccountCanLogIn() throws Exception {
        String token = login("0912345678", "Test1234!");

        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(1))
                .andExpect(jsonPath("$.data.userName").value("王小明"));
    }

    @Test
    @DisplayName("註冊時夾帶的 HTML 被清洗後才入庫")
    void storesSanitisedUserName() throws Exception {
        String phoneNumber = TestData.uniquePhoneNumber();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"phoneNumber":"%s","userName":"<script>alert('xss')</script>小華",
                                 "password":"Test1234!"}
                                """
                                        .formatted(phoneNumber)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.userName").value("小華"));
    }

    @Test
    @DisplayName("SQL 注入字串只是普通字串：登入失敗，不會拿到任何人的權杖")
    void treatsInjectionPayloadAsPlainText() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"phoneNumber":"' OR '1'='1","password":"' OR '1'='1"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("密碼錯誤時不發權杖")
    void rejectsWrongPassword() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"phoneNumber":"0912345678","password":"not-the-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("同一手機號碼重複註冊回 409")
    void rejectsDuplicateRegistration() throws Exception {
        String phoneNumber = TestData.uniquePhoneNumber();
        String body =
                """
                {"phoneNumber":"%s","userName":"重複的人","password":"Test1234!"}
                """
                        .formatted(phoneNumber);

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PHONE_ALREADY_REGISTERED"));
    }

    @Test
    @DisplayName("偽造或缺漏的權杖無法存取受保護資源")
    void rejectsForgedToken() throws Exception {
        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer not.a.real.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        mockMvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("已登入者存取不存在的路徑，取得統一格式的 404")
    void returnsUnifiedNotFoundForAuthenticatedUser() throws Exception {
        String token = login("0912345678", "Test1234!");

        mockMvc.perform(get("/api/no-such-endpoint").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("公開檔案端點不需登入，且不會洩漏手機號碼")
    void exposesPublicProfileWithoutContactDetails() throws Exception {
        mockMvc.perform(get("/api/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userName").value("王小明"))
                .andExpect(jsonPath("$.data.phoneNumber").doesNotExist())
                .andExpect(jsonPath("$.data.email").doesNotExist());
    }

    private String login(String phoneNumber, String password) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"phoneNumber":"%s","password":"%s"}
                                """
                                        .formatted(phoneNumber, password)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode accessToken = objectMapper.readTree(response).path("data").path("accessToken");
        assertThat(accessToken.isTextual()).isTrue();
        return accessToken.asText();
    }
}
