package com.esun.social.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.esun.social.support.ApiClient;
import com.esun.social.support.MySqlContainerSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.stream.IntStream;
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
        mockMvc.perform(get("/api/posts").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].postId").value((int) postId))
                .andExpect(jsonPath("$.data.hasMore").isBoolean());
    }

    @Test
    @DisplayName("游標分頁：第二頁接在第一頁之後，不重複也不遺漏")
    void paginatesWithCursor() throws Exception {
        createPost(author, "A");
        createPost(author, "B");

        String firstPage = mockMvc.perform(get("/api/posts").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasMore").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode first = objectMapper.readTree(firstPage).path("data");
        long firstPostId = first.path("items").get(0).path("postId").asLong();
        String cursor = first.path("nextCursor").asText();

        mockMvc.perform(get("/api/posts").param("size", "1").param("cursor", cursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].postId").value(not(equalTo((int) firstPostId))));
    }

    @Test
    @DisplayName("游標格式錯誤回 400，而不是伺服器錯誤")
    void rejectsMalformedCursor() throws Exception {
        mockMvc.perform(get("/api/posts").param("cursor", "!!!not-a-cursor!!!"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("按讚是冪等的：連按兩次計數仍為 1，取消後歸零")
    void likeAndUnlikeAreIdempotent() throws Exception {
        long postId = createPost(author, "值得一讚");

        mockMvc.perform(post("/api/posts/" + postId + "/likes").header(HttpHeaders.AUTHORIZATION, stranger))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.likeCount").value(1))
                .andExpect(jsonPath("$.data.likedByMe").value(true));

        mockMvc.perform(post("/api/posts/" + postId + "/likes").header(HttpHeaders.AUTHORIZATION, stranger))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.likeCount").value(1));

        mockMvc.perform(delete("/api/posts/" + postId + "/likes").header(HttpHeaders.AUTHORIZATION, stranger))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.likeCount").value(0))
                .andExpect(jsonPath("$.data.likedByMe").value(false));

        mockMvc.perform(delete("/api/posts/" + postId + "/likes").header(HttpHeaders.AUTHORIZATION, stranger))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.likeCount").value(0));
    }

    @Test
    @DisplayName("likedByMe 只反映請求者自己：別人按的讚不會顯示成我按的")
    void likedByMeIsPerViewer() throws Exception {
        long postId = createPost(author, "內容");
        mockMvc.perform(post("/api/posts/" + postId + "/likes").header(HttpHeaders.AUTHORIZATION, stranger))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/posts/" + postId).header(HttpHeaders.AUTHORIZATION, author))
                .andExpect(jsonPath("$.data.likeCount").value(1))
                .andExpect(jsonPath("$.data.likedByMe").value(false));

        // 訪客一律為 false
        mockMvc.perform(get("/api/posts/" + postId)).andExpect(jsonPath("$.data.likedByMe").value(false));
    }

    @Test
    @DisplayName("標籤由請求指定，並可依標籤查回")
    void storesTagsAndFindsByTag() throws Exception {
        long postId = createPost(author, "週末去走走", List.of("陽明山"));

        mockMvc.perform(get("/api/posts/" + postId)).andExpect(jsonPath("$.data.tags[0]").value("陽明山"));

        mockMvc.perform(get("/api/tags/陽明山/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].postId").value((int) postId));
    }

    @Test
    @DisplayName("內文裡的 # 只是文字，不會變成標籤")
    void doesNotParseHashtagsFromContent() throws Exception {
        long postId = createPost(author, "週末去 #陽明山 走走");

        mockMvc.perform(get("/api/posts/" + postId))
                .andExpect(jsonPath("$.data.content").value("週末去 #陽明山 走走"))
                .andExpect(jsonPath("$.data.tags").isEmpty());
    }

    @Test
    @DisplayName("標籤含不合法字元時回 400，訊息與前端逐字相同")
    void rejectsTagWithDisallowedCharacters() throws Exception {
        mockMvc.perform(post("/api/posts")
                        .header(HttpHeaders.AUTHORIZATION, author)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PostBody("週末去走走", List.of("台北101!")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").value("標籤只能使用文字、數字與底線"));
    }

    @Test
    @DisplayName("標籤超過數量上限時回 400，訊息不帶欄位名前綴")
    void rejectsTooManyTags() throws Exception {
        // 上限是 10，這裡刻意送 11 個。訊息直接比對全文而不只比對狀態碼：
        // 這條規則只由 TagNormalizer 負責，若哪天又在 DTO 補上 @Size，
        // 回來的會是被 GlobalExceptionHandler 冠上欄位名的「tags：標籤最多 10 個」，
        // 與前端逐字對齊的那句就對不上了
        List<String> tooManyTags =
                IntStream.rangeClosed(1, 11).mapToObj(index -> "標籤" + index).toList();

        mockMvc.perform(post("/api/posts")
                        .header(HttpHeaders.AUTHORIZATION, author)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PostBody("週末去走走", tooManyTags))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").value("標籤最多 10 個"));
    }

    @Test
    @DisplayName("搜尋：中文關鍵字命中發文內容")
    void searchesByChineseKeyword() throws Exception {
        long postId = createPost(author, "今天煮了一鍋南洋咖哩飯");

        mockMvc.perform(get("/api/posts/search").param("q", "咖哩"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].postId").value((int) postId));
    }

    @Test
    @DisplayName("搜尋關鍵字中的 SQL 與 XSS 樣本都只被當成文字")
    void searchKeywordIsNeverInterpreted() throws Exception {
        mockMvc.perform(get("/api/posts/search").param("q", "' OR '1'='1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray());

        mockMvc.perform(get("/api/posts/search").param("q", "<script>alert(1)</script>"))
                .andExpect(status().isOk());
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
    @DisplayName("編輯時帶新的標籤，舊的標籤整組被換掉")
    void replacesTagsOnEdit() throws Exception {
        long postId = createPost(author, "原始內容", List.of("陽明山", "登山"));

        mockMvc.perform(put("/api/posts/" + postId)
                        .header(HttpHeaders.AUTHORIZATION, author)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PostBody("改成露營", List.of("露營")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tags").value(contains("露營")));

        // 重新查一次：確認換掉的結果真的落在資料庫，而不只是這次回應算出來的
        mockMvc.perform(get("/api/posts/" + postId)).andExpect(jsonPath("$.data.tags").value(contains("露營")));
    }

    /**
     * 標籤獨立成欄位之後才走得通的一條路：在此之前，標籤要變就一定得連內文一起變。
     *
     * <p>能成立是因為 {@code sp_post_update} 以 {@code SELECT COUNT(*)} 判定擁有權，
     * 而不是沿用 {@code UPDATE} 之後的 {@code ROW_COUNT()}——後者回報的是「實際被改變的列數」，
     * 這次更新沒有改變任何一列，會被誤讀成「發文不存在或不屬於你」而回 404。
     * 其餘的標籤測試都同時改了內文，蓋不到這個分支。
     */
    @Test
    @DisplayName("內文一字不動、只換標籤，仍是一次成功的編輯，且不算「已編輯」")
    void replacesTagsWithoutTouchingContent() throws Exception {
        long postId = createPost(author, "原始內容", List.of("陽明山", "登山"));

        // DATETIME 的精度只到秒。不先跨過一秒的邊界，「沒有更新」與「在同一秒內更新過」
        // 在資料上長得一模一樣，下面那條 updatedAt 的斷言就會恆真
        Thread.sleep(1100);

        mockMvc.perform(put("/api/posts/" + postId)
                        .header(HttpHeaders.AUTHORIZATION, author)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PostBody("原始內容", List.of("露營")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("原始內容"))
                .andExpect(jsonPath("$.data.tags").value(contains("露營")));

        // 重新查一次：確認換掉的結果真的落在資料庫，而不只是這次回應算出來的
        String body = mockMvc.perform(get("/api/posts/" + postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("原始內容"))
                .andExpect(jsonPath("$.data.tags").value(contains("露營")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // posts 那一列其實一個欄位都沒變，updated_at 就不該被推進——
        // 卡片上的「已編輯」比對的正是這兩個時間，純換標籤不該讓它冒出來
        JsonNode post = api.data(body);
        assertThat(post.path("updatedAt").asText()).isEqualTo(post.path("createdAt").asText());
    }

    @Test
    @DisplayName("編輯時不帶 tags 欄位，等同把標籤全部拿掉")
    void clearsTagsWhenEditedWithoutTags() throws Exception {
        long postId = createPost(author, "原始內容", List.of("陽明山", "登山"));

        // 請求主體刻意完全沒有 tags 欄位：PUT 是全欄位取代，
        // 「不給」與「給空陣列」在語意上是同一件事，而不是「維持原樣」
        mockMvc.perform(put("/api/posts/" + postId)
                        .header(HttpHeaders.AUTHORIZATION, author)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"改過的內容\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tags").isEmpty());

        mockMvc.perform(get("/api/posts/" + postId))
                .andExpect(jsonPath("$.data.content").value("改過的內容"))
                .andExpect(jsonPath("$.data.tags").isEmpty());
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
        return createPost(bearer, content, List.of());
    }

    private long createPost(String bearer, String content, List<String> tags) throws Exception {
        String body = mockMvc.perform(post("/api/posts")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PostBody(content, tags))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return api.data(body).path("postId").asLong();
    }

    private record PostBody(String content, List<String> tags) {}
}
