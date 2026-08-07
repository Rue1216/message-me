package com.esun.social.presentation.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.esun.social.business.model.Comment;
import com.esun.social.business.service.CommentService;
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

@WebMvcTest(CommentController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class, SecurityErrorWriter.class})
class CommentControllerTest {

    private static final long USER_ID = 7L;

    private static final Comment COMMENT =
            new Comment(
                    5L,
                    1L,
                    USER_ID,
                    "同意",
                    LocalDateTime.of(2026, 1, 1, 10, 0),
                    LocalDateTime.of(2026, 1, 1, 10, 0),
                    "王小明",
                    null,
                    false);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CommentService commentService;

    private static RequestPostProcessor loggedInAs(long userId) {
        return authentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(userId, "0912345678"), null, List.of()));
    }

    @Test
    @DisplayName("列出留言不需登入")
    void listsCommentsWithoutAuthentication() throws Exception {
        when(commentService.listByPost(1L, 1, 20)).thenReturn(PageResponse.of(List.of(COMMENT), 1, 20, 1));

        mockMvc.perform(get("/api/posts/1/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].commentId").value(5))
                .andExpect(jsonPath("$.data.items[0].content").value("同意"))
                .andExpect(jsonPath("$.data.items[0].author.userName").value("王小明"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("新增留言需登入，留言者取自權杖")
    void createsCommentAsAuthenticatedUser() throws Exception {
        when(commentService.create(1L, USER_ID, "同意")).thenReturn(COMMENT);

        mockMvc.perform(post("/api/posts/1/comments")
                        .with(loggedInAs(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"同意\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.commentId").value(5))
                .andExpect(jsonPath("$.data.postId").value(1));

        verify(commentService).create(1L, USER_ID, "同意");
    }

    @Test
    @DisplayName("未登入不得新增或刪除留言")
    void requiresAuthenticationForWrites() throws Exception {
        mockMvc.perform(post("/api/posts/1/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"匿名留言\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/comments/5")).andExpect(status().isUnauthorized());

        verify(commentService, never()).create(anyLong(), anyLong(), anyString());
        verify(commentService, never()).delete(anyLong(), anyLong());
    }

    @Test
    @DisplayName("留言內容為空時回 400")
    void rejectsEmptyContent() throws Exception {
        mockMvc.perform(post("/api/posts/1/comments")
                        .with(loggedInAs(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("對不存在的發文留言回 404")
    void reportsMissingPost() throws Exception {
        when(commentService.create(anyLong(), anyLong(), anyString()))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND, "找不到這篇發文"));

        mockMvc.perform(post("/api/posts/999/comments")
                        .with(loggedInAs(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"留言\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("刪除留言成功回傳沒有內容的成功回應")
    void deletesComment() throws Exception {
        mockMvc.perform(delete("/api/comments/5").with(loggedInAs(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(commentService).delete(5L, USER_ID);
    }
}
