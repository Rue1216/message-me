package com.esun.social.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.esun.social.support.ApiClient;
import com.esun.social.support.MySqlContainerSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** 發文 API 的端到端驗證：權限、分頁、輸入清洗都跑在真實資料庫上。 */
@AutoConfigureMockMvc
class PostApiIT extends MySqlContainerSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private ApiClient api;
    private String author;
    private String stranger;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mockMvc, objectMapper);
        author = api.registerAndLogin("發文的人");
        stranger = api.registerAndLogin("路過的人");
    }

    @Test
    @DisplayName("發文後可在單篇查詢與時間軸列表中看到")
    void createsAndReadsPost() throws Exception {
        long postId = createPost(author, "今天天氣真好");

        mockMvc.perform(get("/api/posts/" + postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("今天天氣真好"))
                .andExpect(jsonPath("$.data.author.userName").value("發文的人"))
                .andExpect(jsonPath("$.data.commentCount").value(0));

        // 新到舊排序，剛發的這篇在第一頁第一筆
        mockMvc.perform(get("/api/posts").param("page", "1").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].postId").value((int) postId))
                .andExpect(jsonPath("$.data.totalElements").isNumber());
    }

    @Test
    @DisplayName("未登入不能發文")
    void requiresLoginToPost() throws Exception {
        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"匿名發文\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("發文內容中的 script 標籤在入庫前就被清掉")
    void sanitisesPostContent() throws Exception {
        long postId = createPost(author, "<script>alert('xss')</script>安全的內容");

        mockMvc.perform(get("/api/posts/" + postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("安全的內容"));
    }

    @Test
    @DisplayName("SQL 注入字串被當成一般文字保存，資料庫不受影響")
    void storesInjectionPayloadVerbatim() throws Exception {
        long postId = createPost(author, "' OR '1'='1");

        mockMvc.perform(get("/api/posts/" + postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("' OR '1'='1"));

        mockMvc.perform(get("/api/posts")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("圖片只接受本站上傳端點產生的路徑")
    void rejectsExternalImageUrl() throws Exception {
        mockMvc.perform(post("/api/posts")
                        .header(HttpHeaders.AUTHORIZATION, author)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"content":"帶圖的發文","image":"https://evil.example.com/track.png"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("本人可以編輯自己的發文")
    void ownerCanEdit() throws Exception {
        long postId = createPost(author, "原始內容");

        mockMvc.perform(put("/api/posts/" + postId)
                        .header(HttpHeaders.AUTHORIZATION, author)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"修改後的內容\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("修改後的內容"));
    }

    @Test
    @DisplayName("別人的發文既不能編輯也不能刪除，且內容不受影響")
    void strangerCanNeitherEditNorDelete() throws Exception {
        long postId = createPost(author, "這是我的發文");

        mockMvc.perform(put("/api/posts/" + postId)
                        .header(HttpHeaders.AUTHORIZATION, stranger)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"我要改掉它\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        mockMvc.perform(delete("/api/posts/" + postId).header(HttpHeaders.AUTHORIZATION, stranger))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/posts/" + postId))
                .andExpect(jsonPath("$.data.content").value("這是我的發文"));
    }

    @Test
    @DisplayName("本人刪除後就查不到了")
    void ownerCanDelete() throws Exception {
        long postId = createPost(author, "刪掉我");

        mockMvc.perform(delete("/api/posts/" + postId).header(HttpHeaders.AUTHORIZATION, author))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/posts/" + postId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("編輯或刪除不存在的發文回 404")
    void reportsMissingPost() throws Exception {
        mockMvc.perform(delete("/api/posts/999999").header(HttpHeaders.AUTHORIZATION, author))
                .andExpect(status().isNotFound());
    }

    private long createPost(String bearer, String content) throws Exception {
        String body = mockMvc.perform(post("/api/posts")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PostBody(content))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return api.data(body).path("postId").asLong();
    }

    private record PostBody(String content) {}
}
