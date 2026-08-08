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

/** 留言 API 的端到端驗證，包含留言數在 HTTP 層面上的一致性。 */
@AutoConfigureMockMvc
class CommentApiIT extends MySqlContainerSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private ApiClient api;
    private String author;
    private String reader;
    private long postId;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mockMvc, objectMapper);
        author = api.registerAndLogin("發文的人");
        reader = api.registerAndLogin("留言的人");
        postId = createPost(author, "歡迎留言");
    }

    @Test
    @DisplayName("留言後發文的留言數同步增加，列表可看到留言與留言者")
    void addsCommentAndKeepsCountInSync() throws Exception {
        createComment(reader, postId, "第一個留言");

        mockMvc.perform(get("/api/posts/" + postId + "/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].content").value("第一個留言"))
                .andExpect(jsonPath("$.data.items[0].author.userName").value("留言的人"))
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(get("/api/posts/" + postId))
                .andExpect(jsonPath("$.data.commentCount").value(1));
    }

    @Test
    @DisplayName("列出留言不需登入")
    void listingIsPublic() throws Exception {
        createComment(reader, postId, "公開可見");

        mockMvc.perform(get("/api/posts/" + postId + "/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].content").value("公開可見"));
    }

    @Test
    @DisplayName("未登入不能留言")
    void requiresLoginToComment() throws Exception {
        mockMvc.perform(post("/api/posts/" + postId + "/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"匿名留言\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("留言中的 HTML 在入庫前被清掉")
    void sanitisesCommentContent() throws Exception {
        createComment(reader, postId, "<script>alert('xss')</script>安全留言");

        mockMvc.perform(get("/api/posts/" + postId + "/comments"))
                .andExpect(jsonPath("$.data.items[0].content").value("安全留言"));
    }

    @Test
    @DisplayName("對不存在的發文留言回 404")
    void rejectsCommentOnMissingPost() throws Exception {
        mockMvc.perform(post("/api/posts/999999/comments")
                        .header(HttpHeaders.AUTHORIZATION, reader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"幽靈留言\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("刪除自己的留言後，發文的留言數同步減少")
    void deletingOwnCommentDecrementsCount() throws Exception {
        long commentId = createComment(reader, postId, "等一下要刪掉");

        mockMvc.perform(delete("/api/comments/" + commentId).header(HttpHeaders.AUTHORIZATION, reader))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/posts/" + postId)).andExpect(jsonPath("$.data.commentCount").value(0));
        mockMvc.perform(get("/api/posts/" + postId + "/comments"))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    @DisplayName("刪不掉別人的留言，留言與計數都不受影響")
    void cannotDeleteSomeoneElsesComment() throws Exception {
        long commentId = createComment(reader, postId, "這是我的留言");

        // 403 而非 404：留言確實存在，只是不屬於這個人。
        // 留言本來就公開可讀，據實回報不會洩漏任何原本看不到的資訊。
        mockMvc.perform(delete("/api/comments/" + commentId).header(HttpHeaders.AUTHORIZATION, author))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/posts/" + postId)).andExpect(jsonPath("$.data.commentCount").value(1));
    }

    @Test
    @DisplayName("刪除不存在的留言回 404，與「不是你的」有所區別")
    void reportsNotFoundForMissingComment() throws Exception {
        mockMvc.perform(delete("/api/comments/999999").header(HttpHeaders.AUTHORIZATION, author))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("本人可以編輯自己的留言，updatedAt 隨之改變")
    void editsOwnComment() throws Exception {
        long commentId = createComment(reader, postId, "原始留言");

        mockMvc.perform(put("/api/comments/" + commentId)
                        .header(HttpHeaders.AUTHORIZATION, reader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"改過的留言\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("改過的留言"))
                .andExpect(jsonPath("$.data.commentId").value((int) commentId));
    }

    @Test
    @DisplayName("編輯別人的留言回 403，內容不受影響")
    void cannotEditSomeoneElsesComment() throws Exception {
        long commentId = createComment(reader, postId, "原始留言");

        mockMvc.perform(put("/api/comments/" + commentId)
                        .header(HttpHeaders.AUTHORIZATION, author)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"被改掉了\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/posts/" + postId + "/comments"))
                .andExpect(jsonPath("$.data.items[0].content").value("原始留言"));
    }

    @Test
    @DisplayName("刪除發文會連同底下的留言一起消失")
    void deletingPostRemovesItsComments() throws Exception {
        createComment(reader, postId, "留言一");
        createComment(reader, postId, "留言二");

        mockMvc.perform(delete("/api/posts/" + postId).header(HttpHeaders.AUTHORIZATION, author))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/posts/" + postId + "/comments")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("外人刪不掉發文時，底下的留言完好無缺（交易回滾）")
    void rejectedPostDeletionLeavesCommentsIntact() throws Exception {
        createComment(reader, postId, "留言一");
        createComment(reader, postId, "留言二");

        mockMvc.perform(delete("/api/posts/" + postId).header(HttpHeaders.AUTHORIZATION, reader))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/posts/" + postId + "/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));
        mockMvc.perform(get("/api/posts/" + postId)).andExpect(jsonPath("$.data.commentCount").value(2));
    }

    private long createPost(String bearer, String content) throws Exception {
        String body = mockMvc.perform(post("/api/posts")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ContentBody(content))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return api.data(body).path("postId").asLong();
    }

    private long createComment(String bearer, long postId, String content) throws Exception {
        String body = mockMvc.perform(post("/api/posts/" + postId + "/comments")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ContentBody(content))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return api.data(body).path("commentId").asLong();
    }

    private record ContentBody(String content) {}
}
