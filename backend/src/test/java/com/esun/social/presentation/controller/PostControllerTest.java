package com.esun.social.presentation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.esun.social.business.model.Post;
import com.esun.social.business.service.PostService;
import com.esun.social.common.config.SecurityConfig;
import com.esun.social.common.exception.BusinessException;
import com.esun.social.common.exception.ErrorCode;
import com.esun.social.common.exception.GlobalExceptionHandler;
import com.esun.social.common.response.CursorPageResponse;
import com.esun.social.common.security.AuthenticatedUser;
import com.esun.social.common.security.SecurityErrorWriter;
import java.time.LocalDateTime;
import java.util.List;
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

@WebMvcTest(PostController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class, SecurityErrorWriter.class})
class PostControllerTest {

    private static final long OWNER_ID = 7L;

    private static final Post POST = new Post(
            1L,
            OWNER_ID,
            "第一篇發文",
            "/uploads/abc.jpg",
            2,
            3,
            true,
            LocalDateTime.of(2026, 1, 1, 9, 0),
            LocalDateTime.of(2026, 1, 1, 9, 0),
            "王小明",
            null,
            false,
            List.of("測試"));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostService postService;

    private static RequestPostProcessor loggedInAs(long userId) {
        return authentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(userId, "0912345678"), null, List.of()));
    }

    @Test
    @DisplayName("列表不需登入，回傳游標分頁資訊與作者資料")
    void listsPostsWithoutAuthentication() throws Exception {
        when(postService.list(null, null, 10))
                .thenReturn(CursorPageResponse.of(List.of(POST), "next-cursor", true));

        mockMvc.perform(get("/api/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].postId").value(1))
                .andExpect(jsonPath("$.data.items[0].content").value("第一篇發文"))
                .andExpect(jsonPath("$.data.items[0].commentCount").value(2))
                .andExpect(jsonPath("$.data.items[0].likeCount").value(3))
                .andExpect(jsonPath("$.data.items[0].likedByMe").value(true))
                .andExpect(jsonPath("$.data.items[0].tags[0]").value("測試"))
                .andExpect(jsonPath("$.data.items[0].author.userId").value(7))
                .andExpect(jsonPath("$.data.items[0].author.userName").value("王小明"))
                .andExpect(jsonPath("$.data.items[0].author.deleted").value(false))
                .andExpect(jsonPath("$.data.nextCursor").value("next-cursor"))
                .andExpect(jsonPath("$.data.hasMore").value(true));
    }

    @Test
    @DisplayName("已登入時把觀看者傳給業務層，用以判斷 likedByMe")
    void passesViewerIdWhenAuthenticated() throws Exception {
        when(postService.list(OWNER_ID, null, 10)).thenReturn(CursorPageResponse.last(List.of(POST)));

        mockMvc.perform(get("/api/posts").with(loggedInAs(OWNER_ID))).andExpect(status().isOk());

        verify(postService).list(OWNER_ID, null, 10);
    }

    @Test
    @DisplayName("游標原樣轉交業務層，控制器不解析它的內容")
    void forwardsCursorVerbatim() throws Exception {
        when(postService.list(null, "abc", 10)).thenReturn(CursorPageResponse.last(List.of()));

        mockMvc.perform(get("/api/posts").param("cursor", "abc")).andExpect(status().isOk());

        verify(postService).list(null, "abc", 10);
    }

    @Test
    @DisplayName("每頁筆數超出範圍時回 400，不會打到業務層")
    void rejectsOutOfRangePaging() throws Exception {
        mockMvc.perform(get("/api/posts").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        mockMvc.perform(get("/api/posts").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verify(postService, never()).list(any(), anyString(), anyInt());
    }

    @Test
    @DisplayName("搜尋端點不會被 /{postId} 吃掉")
    void searchPathIsNotShadowedByPostId() throws Exception {
        when(postService.search(null, "咖哩", null, 10)).thenReturn(CursorPageResponse.last(List.of(POST)));

        mockMvc.perform(get("/api/posts/search").param("q", "咖哩"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].postId").value(1));

        verify(postService).search(null, "咖哩", null, 10);
    }

    @Test
    @DisplayName("按讚需登入，成功後回傳最新的發文狀態")
    void likesPostAsAuthenticatedUser() throws Exception {
        when(postService.like(1L, OWNER_ID)).thenReturn(POST);

        mockMvc.perform(post("/api/posts/1/likes").with(loggedInAs(OWNER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.likeCount").value(3));

        verify(postService).like(1L, OWNER_ID);
    }

    @Test
    @DisplayName("取消按讚走 DELETE")
    void unlikesPost() throws Exception {
        when(postService.unlike(1L, OWNER_ID)).thenReturn(POST);

        mockMvc.perform(delete("/api/posts/1/likes").with(loggedInAs(OWNER_ID)))
                .andExpect(status().isOk());

        verify(postService).unlike(1L, OWNER_ID);
    }

    @Test
    @DisplayName("未登入不得按讚")
    void requiresAuthenticationToLike() throws Exception {
        mockMvc.perform(post("/api/posts/1/likes")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/posts/1/likes")).andExpect(status().isUnauthorized());

        verify(postService, never()).like(anyLong(), anyLong());
    }

    @Test
    @DisplayName("單篇查詢不存在時回 404")
    void reportsMissingPost() throws Exception {
        when(postService.findById(null, 99L)).thenThrow(new BusinessException(ErrorCode.NOT_FOUND, "找不到這篇發文"));

        mockMvc.perform(get("/api/posts/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("新增發文需登入，作者取自權杖")
    void createsPostAsAuthenticatedUser() throws Exception {
        when(postService.create(OWNER_ID, "第一篇發文", null, List.of())).thenReturn(POST);

        mockMvc.perform(post("/api/posts")
                        .with(loggedInAs(OWNER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"第一篇發文\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.postId").value(1));

        verify(postService).create(OWNER_ID, "第一篇發文", null, List.of());
    }

    @Test
    @DisplayName("請求中的標籤原樣轉交業務層")
    void passesTagsToService() throws Exception {
        when(postService.create(OWNER_ID, "第一篇發文", null, List.of("登山"))).thenReturn(POST);

        mockMvc.perform(post("/api/posts")
                        .with(loggedInAs(OWNER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"第一篇發文\",\"tags\":[\"登山\"]}"))
                .andExpect(status().isCreated());

        verify(postService).create(OWNER_ID, "第一篇發文", null, List.of("登山"));
    }

    @Test
    @DisplayName("未登入不得新增、編輯或刪除")
    void requiresAuthenticationForWrites() throws Exception {
        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"想匿名發文\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/posts/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"想匿名改文\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/posts/1")).andExpect(status().isUnauthorized());

        // image 那格用 any() 而非 anyString()：這三個請求都沒有帶圖，image 會是 null，
        // 而 anyString() 不匹配 null——寫成 anyString() 的話，控制器就算真的呼叫了也驗不出來
        verify(postService, never()).create(anyLong(), anyString(), any(), anyList());
    }

    @Test
    @DisplayName("內容為空時回 400")
    void rejectsEmptyContent() throws Exception {
        mockMvc.perform(post("/api/posts")
                        .with(loggedInAs(OWNER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("編輯他人發文回 403")
    void mapsForbiddenToStatus403() throws Exception {
        when(postService.update(anyLong(), anyLong(), anyString(), anyString(), anyList()))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN, "只能編輯或刪除自己的發文"));

        mockMvc.perform(put("/api/posts/1")
                        .with(loggedInAs(999L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"改別人的\",\"image\":\"/uploads/abc.jpg\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("刪除成功回傳沒有內容的成功回應")
    void deletesPost() throws Exception {
        mockMvc.perform(delete("/api/posts/1").with(loggedInAs(OWNER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(postService).delete(1L, OWNER_ID);
    }
}
