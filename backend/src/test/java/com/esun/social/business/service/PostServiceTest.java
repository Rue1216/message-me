package com.esun.social.business.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import com.esun.social.common.response.CursorPageResponse;
import com.esun.social.common.util.Cursor;
import com.esun.social.common.util.HtmlSanitizer;
import com.esun.social.common.util.TagExtractor;
import com.esun.social.data.repository.PostRepository;
import com.esun.social.support.TestData;
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

    private static final Post POST = TestData.post(1L, OWNER_ID, "內容");

    @Mock
    private PostRepository postRepository;

    private PostService postService;

    @BeforeEach
    void setUp() {
        postService = new PostService(postRepository, new HtmlSanitizer(), new TagExtractor());
    }

    @Nested
    @DisplayName("游標分頁")
    class CursorPaging {

        @Test
        @DisplayName("向資料層多要一筆，用來判斷是否還有下一頁")
        void asksForOneExtraRowToDetectMore() {
            when(postRepository.findPageByCursor(eq(OWNER_ID), isNull(), eq(3))).thenReturn(List.of(POST));

            postService.list(OWNER_ID, null, 2);

            verify(postRepository).findPageByCursor(OWNER_ID, null, 3);
        }

        @Test
        @DisplayName("拿到多的那一筆時裁掉它，並標示還有下一頁")
        void trimsExtraRowAndFlagsHasMore() {
            List<Post> three = List.of(
                    TestData.post(3L, OWNER_ID, "第三", LocalDateTime.of(2026, 1, 3, 9, 0)),
                    TestData.post(2L, OWNER_ID, "第二", LocalDateTime.of(2026, 1, 2, 9, 0)),
                    TestData.post(1L, OWNER_ID, "第一", LocalDateTime.of(2026, 1, 1, 9, 0)));
            when(postRepository.findPageByCursor(any(), any(), anyInt())).thenReturn(three);

            CursorPageResponse<Post> page = postService.list(OWNER_ID, null, 2);

            assertThat(page.items()).hasSize(2);
            assertThat(page.hasMore()).isTrue();
            assertThat(page.nextCursor()).isNotNull();
        }

        @Test
        @DisplayName("下一頁的游標指向本頁最後一筆的位置")
        void nextCursorPointsAtLastItemOfPage() {
            LocalDateTime secondCreatedAt = LocalDateTime.of(2026, 1, 2, 9, 0);
            List<Post> three = List.of(
                    TestData.post(3L, OWNER_ID, "第三", LocalDateTime.of(2026, 1, 3, 9, 0)),
                    TestData.post(2L, OWNER_ID, "第二", secondCreatedAt),
                    TestData.post(1L, OWNER_ID, "第一", LocalDateTime.of(2026, 1, 1, 9, 0)));
            when(postRepository.findPageByCursor(any(), any(), anyInt())).thenReturn(three);

            CursorPageResponse<Post> page = postService.list(OWNER_ID, null, 2);

            Cursor.Position decoded = Cursor.decode(page.nextCursor());
            assertThat(decoded.createdAt()).isEqualTo(secondCreatedAt);
            assertThat(decoded.id()).isEqualTo(2L);
        }

        @Test
        @DisplayName("剛好取滿而沒有多的那一筆時，即為最後一頁")
        void marksLastPageWhenNoExtraRow() {
            when(postRepository.findPageByCursor(any(), any(), anyInt())).thenReturn(List.of(POST));

            CursorPageResponse<Post> page = postService.list(OWNER_ID, null, 2);

            assertThat(page.items()).hasSize(1);
            assertThat(page.hasMore()).isFalse();
            assertThat(page.nextCursor()).isNull();
        }

        @Test
        @DisplayName("沒有任何資料時不產生游標")
        void emptyPageHasNoCursor() {
            when(postRepository.findPageByCursor(any(), any(), anyInt())).thenReturn(List.of());

            CursorPageResponse<Post> page = postService.list(OWNER_ID, null, 10);

            assertThat(page.items()).isEmpty();
            assertThat(page.hasMore()).isFalse();
            assertThat(page.nextCursor()).isNull();
        }

        @Test
        @DisplayName("游標格式錯誤回 400，而不是伺服器錯誤")
        void rejectsMalformedCursor() {
            assertThatThrownBy(() -> postService.list(OWNER_ID, "!!!not-base64!!!", 10))
                    .isInstanceOf(BusinessException.class)
                    .asInstanceOf(InstanceOfAssertFactories.type(BusinessException.class))
                    .extracting(BusinessException::errorCode)
                    .isEqualTo(ErrorCode.VALIDATION_ERROR);
        }
    }

    @Nested
    @DisplayName("搜尋")
    class Searching {

        @Test
        @DisplayName("關鍵字先清洗再送進資料層")
        void sanitisesKeyword() {
            when(postRepository.searchByCursor(any(), eq("咖哩"), any(), anyInt())).thenReturn(List.of());

            postService.search(OWNER_ID, "<script>alert(1)</script>咖哩", null, 10);

            verify(postRepository).searchByCursor(OWNER_ID, "咖哩", null, 11);
        }

        @Test
        @DisplayName("關鍵字清洗後為空則直接回空結果，不查詢資料庫")
        void skipsQueryForBlankKeyword() {
            CursorPageResponse<Post> page = postService.search(OWNER_ID, "<b>  </b>", null, 10);

            assertThat(page.items()).isEmpty();
            verify(postRepository, never()).searchByCursor(any(), anyString(), any(), anyInt());
        }
    }

    @Nested
    @DisplayName("標籤")
    class Tagging {

        @Test
        @DisplayName("新增時自內容解析出標籤一併寫入")
        void extractsTagsOnCreate() {
            when(postRepository.create(eq(OWNER_ID), anyString(), isNull(), eq(List.of("登山", "美食"))))
                    .thenReturn(1L);
            when(postRepository.findById(OWNER_ID, 1L)).thenReturn(Optional.of(POST));

            postService.create(OWNER_ID, "今天去 #登山 順便吃 #美食", null);

            verify(postRepository).create(OWNER_ID, "今天去 #登山 順便吃 #美食", null, List.of("登山", "美食"));
        }

        @Test
        @DisplayName("編輯時依新內容重新解析，標籤整組替換")
        void reExtractsTagsOnUpdate() {
            when(postRepository.findById(OWNER_ID, 1L)).thenReturn(Optional.of(POST));
            when(postRepository.update(eq(1L), eq(OWNER_ID), anyString(), isNull(), eq(List.of("露營"))))
                    .thenReturn(true);

            postService.update(1L, OWNER_ID, "改成 #露營", null);

            verify(postRepository).update(1L, OWNER_ID, "改成 #露營", null, List.of("露營"));
        }

        @Test
        @DisplayName("標籤名稱正規化為小寫後才查詢")
        void normalisesTagNameBeforeQuery() {
            when(postRepository.findPageByTag(any(), eq("vue"), any(), anyInt())).thenReturn(List.of());

            postService.listByTag(OWNER_ID, "  VUE  ", null, 10);

            verify(postRepository).findPageByTag(OWNER_ID, "vue", null, 11);
        }
    }

    @Nested
    @DisplayName("按讚")
    class Liking {

        @Test
        @DisplayName("對不存在的發文按讚回 404")
        void reportsNotFoundWhenLikingMissingPost() {
            when(postRepository.like(99L, OWNER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> postService.like(99L, OWNER_ID))
                    .isInstanceOf(BusinessException.class)
                    .asInstanceOf(InstanceOfAssertFactories.type(BusinessException.class))
                    .extracting(BusinessException::errorCode)
                    .isEqualTo(ErrorCode.NOT_FOUND);
        }

        @Test
        @DisplayName("取消對不存在的發文按讚同樣回 404")
        void reportsNotFoundWhenUnlikingMissingPost() {
            when(postRepository.unlike(99L, OWNER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> postService.unlike(99L, OWNER_ID))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("按讚後回傳該篇發文的最新狀態")
        void returnsFreshPostAfterLiking() {
            when(postRepository.like(1L, OWNER_ID)).thenReturn(Optional.of(1));
            when(postRepository.findById(OWNER_ID, 1L)).thenReturn(Optional.of(POST));

            assertThat(postService.like(1L, OWNER_ID)).isEqualTo(POST);
        }
    }

    @Nested
    @DisplayName("新增")
    class Create {

        @Test
        @DisplayName("內容先清洗再寫入")
        void sanitisesContent() {
            when(postRepository.create(eq(OWNER_ID), eq("大家好"), isNull(), eq(List.of())))
                    .thenReturn(1L);
            when(postRepository.findById(OWNER_ID, 1L)).thenReturn(Optional.of(POST));

            postService.create(OWNER_ID, "<script>alert(1)</script>大家好", null);

            verify(postRepository).create(OWNER_ID, "大家好", null, List.of());
        }

        @Test
        @DisplayName("清洗後只剩空字串則拒絕")
        void rejectsContentThatIsOnlyMarkup() {
            assertThatThrownBy(() -> postService.create(OWNER_ID, "<b>  </b>", null))
                    .isInstanceOf(BusinessException.class)
                    .asInstanceOf(InstanceOfAssertFactories.type(BusinessException.class))
                    .extracting(BusinessException::errorCode)
                    .isEqualTo(ErrorCode.VALIDATION_ERROR);

            verify(postRepository, never()).create(anyLong(), anyString(), anyString(), any());
        }

        @Test
        @DisplayName("圖片路徑不是本站上傳格式則拒絕")
        void rejectsForeignImagePath() {
            assertThatThrownBy(() -> postService.create(OWNER_ID, "內容", "https://evil.example.com/x.png"))
                    .isInstanceOf(BusinessException.class);

            verify(postRepository, never()).create(anyLong(), anyString(), anyString(), any());
        }
    }

    @Nested
    @DisplayName("編輯與刪除的權限")
    class Ownership {

        @Test
        @DisplayName("非本人編輯回 403，且不會呼叫更新")
        void refusesToEditSomeoneElsesPost() {
            when(postRepository.findById(STRANGER_ID, 1L)).thenReturn(Optional.of(POST));

            assertThatThrownBy(() -> postService.update(1L, STRANGER_ID, "改掉", null))
                    .isInstanceOf(BusinessException.class)
                    .asInstanceOf(InstanceOfAssertFactories.type(BusinessException.class))
                    .extracting(BusinessException::errorCode)
                    .isEqualTo(ErrorCode.FORBIDDEN);

            verify(postRepository, never()).update(anyLong(), anyLong(), anyString(), isNull(), any());
        }

        @Test
        @DisplayName("非本人刪除回 403，且不會呼叫刪除")
        void refusesToDeleteSomeoneElsesPost() {
            when(postRepository.findById(STRANGER_ID, 1L)).thenReturn(Optional.of(POST));

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
            when(postRepository.findById(OWNER_ID, 99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> postService.delete(99L, OWNER_ID))
                    .isInstanceOf(BusinessException.class)
                    .asInstanceOf(InstanceOfAssertFactories.type(BusinessException.class))
                    .extracting(BusinessException::errorCode)
                    .isEqualTo(ErrorCode.NOT_FOUND);
        }

        @Test
        @DisplayName("本人可以編輯，且更新後回傳最新內容")
        void allowsOwnerToEdit() {
            when(postRepository.findById(OWNER_ID, 1L)).thenReturn(Optional.of(POST));
            when(postRepository.update(1L, OWNER_ID, "改過的內容", null, List.of()))
                    .thenReturn(true);

            Post updated = postService.update(1L, OWNER_ID, "改過的內容", null);

            assertThat(updated).isEqualTo(POST);
            verify(postRepository).update(1L, OWNER_ID, "改過的內容", null, List.of());
        }

        @Test
        @DisplayName("通過權限檢查後資料才消失（競態）時回 404")
        void reportsNotFoundWhenPostVanishesMidRequest() {
            when(postRepository.findById(OWNER_ID, 1L)).thenReturn(Optional.of(POST));
            when(postRepository.delete(1L, OWNER_ID)).thenReturn(false);

            assertThatThrownBy(() -> postService.delete(1L, OWNER_ID))
                    .isInstanceOf(BusinessException.class)
                    .asInstanceOf(InstanceOfAssertFactories.type(BusinessException.class))
                    .extracting(BusinessException::errorCode)
                    .isEqualTo(ErrorCode.NOT_FOUND);
        }
    }
}
