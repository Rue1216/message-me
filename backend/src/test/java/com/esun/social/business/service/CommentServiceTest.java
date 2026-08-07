package com.esun.social.business.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
import com.esun.social.support.TestData;
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
    private static final long STRANGER_ID = 8L;
    private static final long COMMENT_ID = 5L;

    private static final Post POST = TestData.post(POST_ID, USER_ID, "發文");
    private static final Comment COMMENT = TestData.comment(COMMENT_ID, POST_ID, USER_ID, "留言");

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
        when(postRepository.findById(isNull(), eq(POST_ID))).thenReturn(Optional.of(POST));
        when(commentRepository.create(POST_ID, USER_ID, "同意")).thenReturn(COMMENT_ID);
        when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(COMMENT));

        Comment created = commentService.create(POST_ID, USER_ID, "<img src=x onerror=alert(1)>同意");

        assertThat(created).isEqualTo(COMMENT);
        verify(commentRepository).create(POST_ID, USER_ID, "同意");
    }

    @Test
    @DisplayName("對不存在的發文留言回 404，且不會寫入")
    void rejectsCommentOnMissingPost() {
        when(postRepository.findById(isNull(), eq(POST_ID))).thenReturn(Optional.empty());

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
        when(postRepository.findById(isNull(), eq(POST_ID))).thenReturn(Optional.of(POST));

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
        when(postRepository.findById(isNull(), eq(POST_ID))).thenReturn(Optional.of(POST));
        when(commentRepository.countByPost(POST_ID)).thenReturn(30L);
        when(commentRepository.findPageByPost(POST_ID, 10, 10)).thenReturn(List.of(COMMENT));

        PageResponse<Comment> page = commentService.listByPost(POST_ID, 2, 10);

        assertThat(page.items()).containsExactly(COMMENT);
        assertThat(page.totalPages()).isEqualTo(3);
    }

    @Test
    @DisplayName("列出不存在發文的留言回 404")
    void rejectsListingForMissingPost() {
        when(postRepository.findById(isNull(), eq(POST_ID))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.listByPost(POST_ID, 1, 10))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("編輯留言前先清洗內容")
    void sanitisesContentOnUpdate() {
        when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(COMMENT));
        when(commentRepository.update(COMMENT_ID, USER_ID, "改過了")).thenReturn(true);

        commentService.update(COMMENT_ID, USER_ID, "<script>x</script>改過了");

        verify(commentRepository).update(COMMENT_ID, USER_ID, "改過了");
    }

    @Test
    @DisplayName("非本人編輯回 403，且不會呼叫更新")
    void refusesToEditSomeoneElsesComment() {
        when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(COMMENT));

        assertThatThrownBy(() -> commentService.update(COMMENT_ID, STRANGER_ID, "改掉"))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(InstanceOfAssertFactories.type(BusinessException.class))
                .extracting(BusinessException::errorCode)
                .isEqualTo(ErrorCode.FORBIDDEN);

        verify(commentRepository, never()).update(anyLong(), anyLong(), anyString());
    }

    @Test
    @DisplayName("非本人刪除回 403，與「留言不存在」的 404 有所區別")
    void refusesToDeleteSomeoneElsesComment() {
        when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(COMMENT));

        assertThatThrownBy(() -> commentService.delete(COMMENT_ID, STRANGER_ID))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(InstanceOfAssertFactories.type(BusinessException.class))
                .extracting(BusinessException::errorCode)
                .isEqualTo(ErrorCode.FORBIDDEN);

        verify(commentRepository, never()).delete(anyLong(), anyLong());
    }

    @Test
    @DisplayName("留言不存在回 404")
    void reportsNotFoundForMissingComment() {
        when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.delete(COMMENT_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(InstanceOfAssertFactories.type(BusinessException.class))
                .extracting(BusinessException::errorCode)
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("通過權限檢查後留言才消失（競態）時回 404")
    void reportsNotFoundWhenCommentVanishesMidRequest() {
        when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(COMMENT));
        when(commentRepository.delete(COMMENT_ID, USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> commentService.delete(COMMENT_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(InstanceOfAssertFactories.type(BusinessException.class))
                .extracting(BusinessException::errorCode)
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("刪除自己的留言時不拋出任何例外")
    void deletesOwnComment() {
        when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(COMMENT));
        when(commentRepository.delete(COMMENT_ID, USER_ID)).thenReturn(true);

        commentService.delete(COMMENT_ID, USER_ID);

        verify(commentRepository).delete(COMMENT_ID, USER_ID);
    }
}
