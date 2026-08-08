package com.esun.social.data.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.esun.social.business.model.Post;
import com.esun.social.common.util.Cursor;
import com.esun.social.support.MySqlContainerSupport;
import com.esun.social.support.TestData;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PostRepositoryIT extends MySqlContainerSupport {

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    private long authorId;

    @BeforeEach
    void createAuthor() {
        authorId = userRepository.register(TestData.uniquePhoneNumber(), "發文者", null, "hash", "salt");
    }

    @Test
    @DisplayName("新增後可查回，並帶出 JOIN 來的作者資訊")
    void createsAndReadsBackWithAuthor() {
        long postId = postRepository.create(authorId, "測試發文內容", null, List.of());

        assertThat(postRepository.findById(null, postId)).get().satisfies(post -> {
            assertThat(post.content()).isEqualTo("測試發文內容");
            assertThat(post.userId()).isEqualTo(authorId);
            assertThat(post.authorName()).isEqualTo("發文者");
            assertThat(post.commentCount()).isZero();
            assertThat(post.likeCount()).isZero();
            assertThat(post.likedByMe()).isFalse();
            assertThat(post.authorDeleted()).isFalse();
            assertThat(post.tags()).isEmpty();
            assertThat(post.image()).isNull();
            assertThat(post.createdAt()).isNotNull();
        });
    }

    @Test
    @DisplayName("查無發文時回傳空的 Optional")
    void returnsEmptyForUnknownPost() {
        assertThat(postRepository.findById(null, 999_999L)).isEmpty();
    }

    @Test
    @DisplayName("時間軸為新到舊，剛新增的排在最前面")
    void listsNewestFirst() {
        long postId = postRepository.create(authorId, "最新的一篇", null, List.of());

        List<Post> firstPage = postRepository.findPageByCursor(null, null, 5);

        assertThat(firstPage).isNotEmpty();
        assertThat(firstPage.get(0).postId()).isEqualTo(postId);
    }

    @Test
    @DisplayName("游標分頁：第二頁接在第一頁之後，不重複也不遺漏")
    void paginatesWithCursor() {
        postRepository.create(authorId, "A", null, List.of());
        postRepository.create(authorId, "B", null, List.of());

        List<Post> firstPage = postRepository.findPageByCursor(null, null, 1);
        assertThat(firstPage).hasSize(1);

        Post last = firstPage.get(0);
        Cursor.Position cursor = new Cursor.Position(last.createdAt(), last.postId());
        List<Post> secondPage = postRepository.findPageByCursor(null, cursor, 1);

        assertThat(secondPage).hasSize(1);
        assertThat(secondPage.get(0).postId()).isNotEqualTo(last.postId());
    }

    @Test
    @DisplayName("多取一筆的契約：資料足夠時會回傳 limit 指定的筆數")
    void returnsUpToRequestedLimit() {
        postRepository.create(authorId, "A", null, List.of());
        postRepository.create(authorId, "B", null, List.of());
        postRepository.create(authorId, "C", null, List.of());

        assertThat(postRepository.findPageByCursor(null, null, 2)).hasSize(2);
    }

    @Test
    @DisplayName("本人可以編輯自己的發文")
    void ownerCanUpdate() {
        long postId = postRepository.create(authorId, "原始內容", null, List.of());

        boolean updated = postRepository.update(postId, authorId, "修改後的內容", "/uploads/x.jpg", List.of());

        assertThat(updated).isTrue();
        assertThat(postRepository.findById(null, postId)).get().satisfies(post -> {
            assertThat(post.content()).isEqualTo("修改後的內容");
            assertThat(post.image()).isEqualTo("/uploads/x.jpg");
        });
    }

    @Test
    @DisplayName("內容完全未改動時仍回報成功，不會被誤判為無權限")
    void reportsSuccessWhenNothingActuallyChanged() {
        long postId = postRepository.create(authorId, "原封不動", null, List.of());

        assertThat(postRepository.update(postId, authorId, "原封不動", null, List.of()))
                .isTrue();
    }

    @Test
    @DisplayName("即使繞過業務層，SP 也不會讓別人改到這篇發文")
    void strangerCannotUpdateAtDatabaseLevel() {
        long postId = postRepository.create(authorId, "原始內容", null, List.of());
        long strangerId = userRepository.register(TestData.uniquePhoneNumber(), "路人", null, "hash", "salt");

        boolean updated = postRepository.update(postId, strangerId, "被別人改掉了", null, List.of());

        assertThat(updated).isFalse();
        assertThat(postRepository.findById(null, postId))
                .get()
                .extracting(Post::content)
                .isEqualTo("原始內容");
    }

    @Test
    @DisplayName("本人可以刪除自己的發文")
    void ownerCanDelete() {
        long postId = postRepository.create(authorId, "即將被刪除", null, List.of());

        assertThat(postRepository.delete(postId, authorId)).isTrue();
        assertThat(postRepository.findById(null, postId)).isEmpty();
    }

    @Test
    @DisplayName("即使繞過業務層，SP 也不會讓別人刪掉這篇發文")
    void strangerCannotDeleteAtDatabaseLevel() {
        long postId = postRepository.create(authorId, "別人的發文", null, List.of());
        long strangerId = userRepository.register(TestData.uniquePhoneNumber(), "路人", null, "hash", "salt");

        assertThat(postRepository.delete(postId, strangerId)).isFalse();
        assertThat(postRepository.findById(null, postId)).isPresent();
    }

    @Test
    @DisplayName("SQL 注入字串被當成普通內容存取，不影響查詢結果")
    void storesInjectionPayloadAsPlainContent() {
        String payload = "'; DROP TABLE posts; -- ";

        long postId = postRepository.create(authorId, payload, null, List.of());

        assertThat(postRepository.findById(null, postId))
                .get()
                .extracting(Post::content)
                .isEqualTo(payload);
        // 資料表還在
        assertThat(postRepository.findPageByCursor(null, null, 1)).isNotEmpty();
    }

    @Test
    @DisplayName("標籤隨發文一併寫入，重複的名稱只算一次")
    void attachesTagsOnCreate() {
        long postId = postRepository.create(authorId, "內容", null, List.of("登山", "美食", "登山"));

        assertThat(postRepository.findById(null, postId))
                .get()
                .extracting(Post::tags)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.list(String.class))
                .containsExactlyInAnyOrder("登山", "美食");
    }

    @Test
    @DisplayName("編輯時標籤整組替換，舊標籤不會殘留")
    void replacesTagsOnUpdate() {
        long postId = postRepository.create(authorId, "內容", null, List.of("舊標籤"));

        postRepository.update(postId, authorId, "新內容", null, List.of("新標籤"));

        assertThat(postRepository.findById(null, postId))
                .get()
                .extracting(Post::tags)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.list(String.class))
                .containsExactly("新標籤");
    }

    @Test
    @DisplayName("依標籤查詢只回傳掛著該標籤的發文")
    void findsPostsByTag() {
        long tagged = postRepository.create(authorId, "有標籤", null, List.of("露營"));
        postRepository.create(authorId, "沒標籤", null, List.of());

        List<Post> found = postRepository.findPageByTag(null, "露營", null, 10);

        assertThat(found).extracting(Post::postId).contains(tagged);
        assertThat(found).allSatisfy(post -> assertThat(post.tags()).contains("露營"));
    }

    @Test
    @DisplayName("按讚是冪等的：連按兩次計數只加一")
    void likeIsIdempotent() {
        long postId = postRepository.create(authorId, "值得一讚", null, List.of());

        assertThat(postRepository.like(postId, authorId)).contains(1);
        assertThat(postRepository.like(postId, authorId)).contains(1);
    }

    @Test
    @DisplayName("取消按讚同樣冪等，且計數不會變成負數")
    void unlikeIsIdempotent() {
        long postId = postRepository.create(authorId, "值得一讚", null, List.of());
        postRepository.like(postId, authorId);

        assertThat(postRepository.unlike(postId, authorId)).contains(0);
        assertThat(postRepository.unlike(postId, authorId)).contains(0);
    }

    @Test
    @DisplayName("對不存在的發文按讚回傳空的 Optional，供業務層回 404")
    void likingMissingPostReportsAbsence() {
        assertThat(postRepository.like(999_999L, authorId)).isEmpty();
    }

    @Test
    @DisplayName("likedByMe 取決於觀看者是誰")
    void likedByMeDependsOnViewer() {
        long postId = postRepository.create(authorId, "內容", null, List.of());
        long otherId = userRepository.register(TestData.uniquePhoneNumber(), "另一人", null, "hash", "salt");
        postRepository.like(postId, authorId);

        assertThat(postRepository.findById(authorId, postId)).get().satisfies(post -> {
            assertThat(post.likedByMe()).isTrue();
            assertThat(post.likeCount()).isEqualTo(1);
        });
        assertThat(postRepository.findById(otherId, postId))
                .get()
                .extracting(Post::likedByMe)
                .isEqualTo(false);
        // 訪客（未登入）一律為 false
        assertThat(postRepository.findById(null, postId))
                .get()
                .extracting(Post::likedByMe)
                .isEqualTo(false);
    }

    @Test
    @DisplayName("刪除發文會連帶清掉按讚與標籤關聯")
    void deleteCascadesLikesAndTags() {
        long postId = postRepository.create(authorId, "內容", null, List.of("待刪標籤"));
        postRepository.like(postId, authorId);

        assertThat(postRepository.delete(postId, authorId)).isTrue();
        // 關聯若沒清乾淨，外鍵會讓上面的刪除直接失敗，因此能刪掉本身就是證明
        assertThat(postRepository.findPageByTag(null, "待刪標籤", null, 10)).isEmpty();
    }

    @Test
    @DisplayName("搜尋：中文關鍵字命中內容")
    void searchesChineseKeyword() {
        postRepository.create(authorId, "今天煮了一鍋南洋咖哩", null, List.of());

        assertThat(postRepository.searchByCursor(null, "咖哩", null, 10))
                .extracting(Post::content)
                .anySatisfy(content -> assertThat(content).contains("咖哩"));
    }

    @Test
    @DisplayName("搜尋：萬用字元被跳脫，不會比對到全部發文")
    void escapesWildcardsInSearch() {
        postRepository.create(authorId, "一般內容", null, List.of());

        assertThat(postRepository.searchByCursor(null, "%", null, 10)).isEmpty();
    }
}
