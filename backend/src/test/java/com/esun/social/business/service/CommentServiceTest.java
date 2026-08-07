package com.esun.social.business.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.esun.social.business.model.Comment;
import com.esun.social.business.model.Post;
import com.esun.social.common.exception.BusinessException;
import com.esun.social.common.exception.ErrorCode;
import com.esun.social.common.response.PageResponse;
import com.esun.social.common.util.HtmlSanitizer;
import com.esun.social.data.repository.CommentRepository;
import com.esun.social.data.repository.PostRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    private static final long POST_ID = 1L;
    private static final long USER_ID = 7L;

    private static final Post POST = new Post(
            POST_ID,
            USER_ID,
            "發文",
            null,
            0,
            LocalDateTime.of(2026, 1, 1, 9, 0),
            LocalDateTime.of(2026, 1, 1, 9, 0),
            "王小明",
            null);

    private static final Comment COMMENT =
            new Comment(5L, POST_ID, USER_ID, "留言", LocalDateTime.of(2026, 1, 1, 10, 0), "王小明", null);

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private PostRepository postRepository;

    private CommentService commentService;

    @BeforeEach
    void setUp() {
        commentService = new CommentService(commentRepository, postRepository, new HtmlSanitizer());
    }

    @Test
    @DisplayName("留言內容先清洗再寫入")
    void sanitisesContentBeforeStoring() {
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(POST));
        when(commentRepository.create(POST_ID, USER_ID, "同意")).thenReturn(5L);
        when(commentRepository.countByPost(POST_ID)).thenReturn(1L);
        when(commentRepository.findPageByPost(anyLong(), anyInt(), anyInt())).thenReturn(List.of(COMMENT));

        Comment created = commentService.create(POST_ID, USER_ID, "<img src=x onerror=alert(1)>同意");

        assertThat(created).isEqualTo(COMMENT);
        verify(commentRepository).create(POST_ID, USER_ID, "同意");
    }

    @Test
    @DisplayName("對不存在的發文留言回 404，且不會寫入")
    void rejectsCommentOnMissingPost() {
        when(postRepository.findById(POST_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.create(POST_ID, USER_ID, "留言"))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(InstanceOfAssertFactories.type(BusinessException.class))
                .extracting(BusinessException::errorCode)
                .isEqualTo(ErrorCode.NOT_FOUND);

        verify(commentRepository, never()).create(anyLong(), anyLong(), anyString());
    }

    @Test
    @DisplayName("內容清洗後只剩空字串則拒絕")
    void rejectsContentThatIsOnlyMarkup() {
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(POST));

        assertThatThrownBy(() -> commentService.create(POST_ID, USER_ID, "<b> </b>"))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(InstanceOfAssertFactories.type(BusinessException.class))
                .extracting(BusinessException::errorCode)
                .isEqualTo(ErrorCode.VALIDATION_ERROR);

        verify(commentRepository, never()).create(anyLong(), anyLong(), anyString());
    }

    @Test
    @DisplayName("列出留言時換算頁碼為 offset")
    void translatesPageNumberToOffset() {
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(POST));
        when(commentRepository.countByPost(POST_ID)).thenReturn(30L);
        when(commentRepository.findPageByPost(POST_ID, 10, 10)).thenReturn(List.of(COMMENT));

        PageResponse<Comment> page = commentService.listByPost(POST_ID, 2, 10);

        assertThat(page.items()).containsExactly(COMMENT);
        assertThat(page.totalPages()).isEqualTo(3);
    }

    @Test
    @DisplayName("列出不存在發文的留言回 404")
    void rejectsListingForMissingPost() {
        when(postRepository.findById(POST_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.listByPost(POST_ID, 1, 10))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("刪不到留言（不存在或不是自己的）一律回 404")
    void reportsNotFoundWhenNothingDeleted() {
        when(commentRepository.delete(5L, USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> commentService.delete(5L, USER_ID))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(InstanceOfAssertFactories.type(BusinessException.class))
                .extracting(BusinessException::errorCode)
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("刪除成功時不拋出任何例外")
    void deletesOwnComment() {
        when(commentRepository.delete(5L, USER_ID)).thenReturn(true);

        commentService.delete(5L, USER_ID);

        verify(commentRepository).delete(5L, USER_ID);
    }
}
