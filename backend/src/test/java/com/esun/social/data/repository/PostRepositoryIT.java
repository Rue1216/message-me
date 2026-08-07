package com.esun.social.data.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.esun.social.business.model.Post;
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
        long postId = postRepository.create(authorId, "測試發文內容", null);

        assertThat(postRepository.findById(postId)).get().satisfies(post -> {
            assertThat(post.content()).isEqualTo("測試發文內容");
            assertThat(post.userId()).isEqualTo(authorId);
            assertThat(post.authorName()).isEqualTo("發文者");
            assertThat(post.commentCount()).isZero();
            assertThat(post.image()).isNull();
            assertThat(post.createdAt()).isNotNull();
        });
    }

    @Test
    @DisplayName("查無發文時回傳空的 Optional")
    void returnsEmptyForUnknownPost() {
        assertThat(postRepository.findById(999_999L)).isEmpty();
    }

    @Test
    @DisplayName("列表為新到舊，剛新增的排在最前面")
    void listsNewestFirst() {
        long postId = postRepository.create(authorId, "最新的一篇", null);

        List<Post> firstPage = postRepository.findPage(5, 0);

        assertThat(firstPage).isNotEmpty();
        assertThat(firstPage.get(0).postId()).isEqualTo(postId);
        assertThat(postRepository.count()).isGreaterThanOrEqualTo(firstPage.size());
    }

    @Test
    @DisplayName("offset 生效：第二頁不會重複第一頁的內容")
    void paginatesWithOffset() {
        postRepository.create(authorId, "A", null);
        postRepository.create(authorId, "B", null);

        List<Post> firstPage = postRepository.findPage(1, 0);
        List<Post> secondPage = postRepository.findPage(1, 1);

        assertThat(firstPage).hasSize(1);
        assertThat(secondPage).hasSize(1);
        assertThat(firstPage.get(0).postId()).isNotEqualTo(secondPage.get(0).postId());
    }

    @Test
    @DisplayName("本人可以編輯自己的發文")
    void ownerCanUpdate() {
        long postId = postRepository.create(authorId, "原始內容", null);

        boolean updated = postRepository.update(postId, authorId, "修改後的內容", "/uploads/x.jpg");

        assertThat(updated).isTrue();
        assertThat(postRepository.findById(postId)).get().satisfies(post -> {
            assertThat(post.content()).isEqualTo("修改後的內容");
            assertThat(post.image()).isEqualTo("/uploads/x.jpg");
        });
    }

    @Test
    @DisplayName("即使繞過業務層，SP 也不會讓別人改到這篇發文")
    void strangerCannotUpdateAtDatabaseLevel() {
        long postId = postRepository.create(authorId, "原始內容", null);
        long strangerId = userRepository.register(TestData.uniquePhoneNumber(), "路人", null, "hash", "salt");

        boolean updated = postRepository.update(postId, strangerId, "被別人改掉了", null);

        assertThat(updated).isFalse();
        assertThat(postRepository.findById(postId))
                .get()
                .extracting(Post::content)
                .isEqualTo("原始內容");
    }

    @Test
    @DisplayName("本人可以刪除自己的發文")
    void ownerCanDelete() {
        long postId = postRepository.create(authorId, "即將被刪除", null);

        assertThat(postRepository.delete(postId, authorId)).isTrue();
        assertThat(postRepository.findById(postId)).isEmpty();
    }

    @Test
    @DisplayName("即使繞過業務層，SP 也不會讓別人刪掉這篇發文")
    void strangerCannotDeleteAtDatabaseLevel() {
        long postId = postRepository.create(authorId, "別人的發文", null);
        long strangerId = userRepository.register(TestData.uniquePhoneNumber(), "路人", null, "hash", "salt");

        assertThat(postRepository.delete(postId, strangerId)).isFalse();
        assertThat(postRepository.findById(postId)).isPresent();
    }

    @Test
    @DisplayName("SQL 注入字串被當成普通內容存取，不影響查詢結果")
    void storesInjectionPayloadAsPlainContent() {
        String payload = "'; DROP TABLE posts; -- ";

        long postId = postRepository.create(authorId, payload, null);

        assertThat(postRepository.findById(postId)).get().extracting(Post::content).isEqualTo(payload);
        // 資料表還在
        assertThat(postRepository.count()).isPositive();
    }
}
