package com.esun.social.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.esun.social.support.ApiClient;
import com.esun.social.support.MySqlContainerSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/** 圖片上傳與個人檔案更新的端到端驗證。 */
@AutoConfigureMockMvc
class UploadAndProfileIT extends MySqlContainerSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private ApiClient api;
    private String bearer;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mockMvc, objectMapper);
        bearer = api.registerAndLogin("上傳測試者");
    }

    @Test
    @DisplayName("上傳圖片後可直接由該路徑取回同一份內容")
    void uploadsAndServesImage() throws Exception {
        String url = upload(pngFile());

        byte[] served = mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        assertThat(served).isEqualTo(pngFile().getBytes());
    }

    @Test
    @DisplayName("未登入不能上傳")
    void requiresLoginToUpload() throws Exception {
        mockMvc.perform(multipart("/api/files/images").file(pngFile()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("改了副檔名的非圖片檔案會被內容嗅探擋下")
    void rejectsDisguisedFile() throws Exception {
        MockMultipartFile disguised = new MockMultipartFile(
                "file", "cat.png", "image/png", "<?php system($_GET['c']); ?>".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/files/images").file(disguised).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    @DisplayName("上傳的圖片可用於發文")
    void usesUploadedImageInPost() throws Exception {
        String url = upload(pngFile());

        mockMvc.perform(post("/api/posts")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"content":"看我拍的照片","image":"%s"}
                                """
                                        .formatted(url)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.image").value(url));
    }

    @Test
    @DisplayName("更新個人檔案：名稱、自介與封面都寫入，未提供的欄位被清空")
    void updatesProfile() throws Exception {
        String url = upload(pngFile());

        mockMvc.perform(put("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"userName":"改過的名字","email":"new@example.com",
                                 "biography":"新的自我介紹","coverImage":"%s"}
                                """
                                        .formatted(url)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userName").value("改過的名字"))
                .andExpect(jsonPath("$.data.coverImage").value(url));

        // PUT 為全欄位取代：這次沒給 email 與 biography，兩者應被清空
        mockMvc.perform(put("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userName\":\"只剩名字\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(jsonPath("$.data.userName").value("只剩名字"))
                .andExpect(jsonPath("$.data.email").doesNotExist())
                .andExpect(jsonPath("$.data.biography").doesNotExist());
    }

    @Test
    @DisplayName("個人檔案中的 HTML 被清洗，封面只接受本站上傳路徑")
    void sanitisesProfileAndValidatesCover() throws Exception {
        mockMvc.perform(put("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"userName":"<script>alert(1)</script>安全的名字","biography":"<b>粗體</b>自介"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userName").value("安全的名字"))
                .andExpect(jsonPath("$.data.biography").value("粗體自介"));

        mockMvc.perform(put("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"userName":"名字","coverImage":"https://evil.example.com/x.png"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("未登入不能更新個人檔案")
    void requiresLoginToUpdateProfile() throws Exception {
        mockMvc.perform(put("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userName\":\"匿名改檔案\"}"))
                .andExpect(status().isUnauthorized());
    }

    private String upload(MockMultipartFile file) throws Exception {
        String body = mockMvc.perform(
                        multipart("/api/files/images").file(file).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return api.data(body).path("url").asText();
    }

    /** 最小的合法 PNG 標頭加上一段內容，足以通過位元組特徵判斷。 */
    private static MockMultipartFile pngFile() {
        byte[] content = new byte[48];
        byte[] magic = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        System.arraycopy(magic, 0, content, 0, magic.length);
        for (int i = magic.length; i < content.length; i++) {
            content[i] = (byte) i;
        }
        return new MockMultipartFile("file", "photo.png", "image/png", content);
    }
}
