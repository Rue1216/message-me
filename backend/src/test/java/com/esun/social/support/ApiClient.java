package com.esun.social.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 整合測試用的小幫手：把「準備一個已登入的使用者」這件事收成一行。
 *
 * <p>刻意走真正的註冊與登入端點，而不是直接塞一個權杖進去——這樣每個測試的前置條件
 * 本身也在驗證那兩支端點還活著。
 */
public class ApiClient {

    public static final String PASSWORD = "Test1234!";

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    public ApiClient(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    /** 註冊一位新使用者並登入，回傳可直接放進 Authorization 標頭的 {@code Bearer <token>}。 */
    public String registerAndLogin(String userName) throws Exception {
        String phoneNumber = TestData.uniquePhoneNumber();
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"phoneNumber":"%s","userName":"%s","password":"%s"}
                                """
                                        .formatted(phoneNumber, userName, PASSWORD)))
                .andExpect(status().isCreated());
        return bearerFor(phoneNumber);
    }

    /** 以既有帳號登入，回傳 {@code Bearer <token>}。 */
    public String bearerFor(String phoneNumber) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"phoneNumber":"%s","password":"%s"}
                                """
                                        .formatted(phoneNumber, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return "Bearer " + objectMapper.readTree(body).path("data").path("accessToken").asText();
    }

    /** 取出回應主體中 {@code data} 之下的某個欄位。 */
    public JsonNode data(String responseBody) throws Exception {
        return objectMapper.readTree(responseBody).path("data");
    }
}
