package com.esun.social.presentation.controller;

import static org.mockito.ArgumentMatchers.anyInt;
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
import com.esun.social.common.response.PageResponse;
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
            LocalDateTime.of(2026, 1, 1, 9, 0),
            LocalDateTime.of(2026, 1, 1, 9, 0),
            "王小明",
            null);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostService postService;

    private static RequestPostProcessor loggedInAs(long userId) {
        return authentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(userId, "0912345678"), null, List.of()));
    }

    @Test
    @DisplayName("列表不需登入，回傳分頁資訊與作者資料")
    void listsPostsWithoutAuthentication() throws Exception {
        when(postService.list(1, 10)).thenReturn(PageResponse.of(List.of(POST), 1, 10, 1));

        mockMvc.perform(get("/api/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].postId").value(1))
                .andExpect(jsonPath("$.data.items[0].content").value("第一篇發文"))
                .andExpect(jsonPath("$.data.items[0].commentCount").value(2))
                .andExpect(jsonPath("$.data.items[0].author.userId").value(7))
                .andExpect(jsonPath("$.data.items[0].author.userName").value("王小明"))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1));
    }

    @Test
    @DisplayName("分頁參數超出範圍時回 400，不會打到業務層")
    void rejectsOutOfRangePaging() throws Exception {
        mockMvc.perform(get("/api/posts").param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        mockMvc.perform(get("/api/posts").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verify(postService, never()).list(anyInt(), anyInt());
    }

    @Test
    @DisplayName("單篇查詢不存在時回 404")
    void reportsMissingPost() throws Exception {
        when(postService.findById(99L)).thenThrow(new BusinessException(ErrorCode.NOT_FOUND, "找不到這篇發文"));

        mockMvc.perform(get("/api/posts/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("新增發文需登入，作者取自權杖")
    void createsPostAsAuthenticatedUser() throws Exception {
        when(postService.create(OWNER_ID, "第一篇發文", null)).thenReturn(POST);

        mockMvc.perform(post("/api/posts")
                        .with(loggedInAs(OWNER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"第一篇發文\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.postId").value(1));

        verify(postService).create(OWNER_ID, "第一篇發文", null);
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

        verify(postService, never()).create(anyLong(), anyString(), anyString());
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
        when(postService.update(anyLong(), anyLong(), anyString(), anyString()))
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
