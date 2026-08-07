package com.esun.social.business.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.esun.social.business.model.Post;
import com.esun.social.common.exception.BusinessException;
import com.esun.social.common.exception.ErrorCode;
import com.esun.social.common.response.PageResponse;
import com.esun.social.common.util.HtmlSanitizer;
import com.esun.social.data.repository.PostRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    private static final long OWNER_ID = 7L;
    private static final long STRANGER_ID = 8L;

    private static final Post POST = new Post(
            1L,
            OWNER_ID,
            "內容",
            null,
            2,
            LocalDateTime.of(2026, 1, 1, 9, 0),
            LocalDateTime.of(2026, 1, 1, 9, 0),
            "王小明",
            null);

    @Mock
    private PostRepository postRepository;

    private PostService postService;

    @BeforeEach
    void setUp() {
        postService = new PostService(postRepository, new HtmlSanitizer());
    }

    @Nested
    @DisplayName("分頁列表")
    class Listing {

        @Test
        @DisplayName("頁碼自 1 起算，換算成 SP 需要的 offset")
        void translatesPageNumberToOffset() {
            when(postRepository.count()).thenReturn(25L);
            when(postRepository.findPage(10, 20)).thenReturn(List.of(POST));

            PageResponse<Post> page = postService.list(3, 10);

            assertThat(page.items()).containsExactly(POST);
            assertThat(page.page()).isEqualTo(3);
            assertThat(page.totalElements()).isEqualTo(25);
            assertThat(page.totalPages()).isEqualTo(3);
        }

        @Test
        @DisplayName("超出總筆數的頁碼回空清單，且不再查詢資料庫")
        void returnsEmptyPageBeyondLastPage() {
            when(postRepository.count()).thenReturn(5L);

            PageResponse<Post> page = postService.list(99, 10);

            assertThat(page.items()).isEmpty();
            assertThat(page.totalElements()).isEqualTo(5);
            verify(postRepository, never()).findPage(anyInt(), anyInt());
        }
    }

    @Nested
    @DisplayName("新增")
    class Create {

        @Test
        @DisplayName("內容先清洗再寫入")
        void sanitisesContent() {
            when(postRepository.create(eq(OWNER_ID), eq("大家好"), isNull())).thenReturn(1L);
            when(postRepository.findById(1L)).thenReturn(Optional.of(POST));

            postService.create(OWNER_ID, "<script>alert(1)</script>大家好", null);

            verify(postRepository).create(OWNER_ID, "大家好", null);
        }

        @Test
        @DisplayName("清洗後只剩空字串則拒絕")
        void rejectsContentThatIsOnlyMarkup() {
            assertThatThrownBy(() -> postService.create(OWNER_ID, "<b>  </b>", null))
                    .isInstanceOf(BusinessException.class)
                    .asInstanceOf(InstanceOfAssertFactories.type(BusinessException.class))
                    .extracting(BusinessException::errorCode)
                    .isEqualTo(ErrorCode.VALIDATION_ERROR);

            verify(postRepository, never()).create(anyLong(), anyString(), anyString());
        }

        @Test
        @DisplayName("圖片路徑不是本站上傳格式則拒絕")
        void rejectsForeignImagePath() {
            assertThatThrownBy(() -> postService.create(OWNER_ID, "內容", "https://evil.example.com/x.png"))
                    .isInstanceOf(BusinessException.class);

            verify(postRepository, never()).create(anyLong(), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("編輯與刪除的權限")
    class Ownership {

        @Test
        @DisplayName("非本人編輯回 403，且不會呼叫更新")
        void refusesToEditSomeoneElsesPost() {
            when(postRepository.findById(1L)).thenReturn(Optional.of(POST));

            assertThatThrownBy(() -> postService.update(1L, STRANGER_ID, "改掉", null))
                    .isInstanceOf(BusinessException.class)
                    .asInstanceOf(InstanceOfAssertFactories.type(BusinessException.class))
                    .extracting(BusinessException::errorCode)
                    .isEqualTo(ErrorCode.FORBIDDEN);

            verify(postRepository, never()).update(anyLong(), anyLong(), anyString(), isNull());
        }

        @Test
        @DisplayName("非本人刪除回 403，且不會呼叫刪除")
        void refusesToDeleteSomeoneElsesPost() {
            when(postRepository.findById(1L)).thenReturn(Optional.of(POST));

            assertThatThrownBy(() -> postService.delete(1L, STRANGER_ID))
                    .isInstanceOf(BusinessException.class)
                    .asInstanceOf(InstanceOfAssertFactories.type(BusinessException.class))
                    .extracting(BusinessException::errorCode)
                    .isEqualTo(ErrorCode.FORBIDDEN);

            verify(postRepository, never()).delete(anyLong(), anyLong());
        }

        @Test
        @DisplayName("發文不存在回 404，與權限不足有所區別")
        void reportsMissingPostSeparatelyFromForbidden() {
            when(postRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> postService.delete(99L, OWNER_ID))
                    .isInstanceOf(BusinessException.class)
                    .asInstanceOf(InstanceOfAssertFactories.type(BusinessException.class))
                    .extracting(BusinessException::errorCode)
                    .isEqualTo(ErrorCode.NOT_FOUND);
        }

        @Test
        @DisplayName("本人可以編輯，且更新後回傳最新內容")
        void allowsOwnerToEdit() {
            when(postRepository.findById(1L)).thenReturn(Optional.of(POST));
            when(postRepository.update(1L, OWNER_ID, "改過的內容", null)).thenReturn(true);

            Post updated = postService.update(1L, OWNER_ID, "改過的內容", null);

            assertThat(updated).isEqualTo(POST);
            verify(postRepository).update(1L, OWNER_ID, "改過的內容", null);
        }

        @Test
        @DisplayName("通過權限檢查後資料才消失（競態）時回 404")
        void reportsNotFoundWhenPostVanishesMidRequest() {
            when(postRepository.findById(1L)).thenReturn(Optional.of(POST));
            when(postRepository.delete(1L, OWNER_ID)).thenReturn(false);

            assertThatThrownBy(() -> postService.delete(1L, OWNER_ID))
                    .isInstanceOf(BusinessException.class)
                    .asInstanceOf(InstanceOfAssertFactories.type(BusinessException.class))
                    .extracting(BusinessException::errorCode)
                    .isEqualTo(ErrorCode.NOT_FOUND);
        }
    }
}
