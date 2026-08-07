package com.esun.social.data.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.esun.social.business.model.Comment;
import com.esun.social.business.model.Post;
import com.esun.social.common.exception.BusinessException;
import com.esun.social.support.MySqlContainerSupport;
import com.esun.social.support.TestData;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 留言資料存取的整合測試，重點在<strong>跨資料表異動的交易語意</strong>。
 *
 * <p>規格要求「跨表異動必須包在 Transaction 中」。這裡驗證的不只是「成功時兩張表都改了」，
 * 更重要的是「失敗時兩張表都沒改」——後者才是交易真正的價值，也是最容易寫錯的部分。
 */
class CommentRepositoryIT extends MySqlContainerSupport {

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    private long authorId;
    private long postId;

    @BeforeEach
    void createPost() {
        authorId = userRepository.register(TestData.uniquePhoneNumber(), "發文者", null, "hash", "salt");
        postId = postRepository.create(authorId, "可以留言的發文", null, java.util.List.of());
    }

    @Test
    @DisplayName("新增留言後可分頁取回，並帶出留言者資訊")
    void createsAndListsComments() {
        commentRepository.create(postId, authorId, "第一則留言");
        commentRepository.create(postId, authorId, "第二則留言");

        List<Comment> comments = commentRepository.findPageByPost(postId, 10, 0);

        assertThat(comments).hasSize(2);
        // 留言由舊到新
        assertThat(comments).extracting(Comment::content).containsExactly("第一則留言", "第二則留言");
        assertThat(comments.get(0).authorName()).isEqualTo("發文者");
        assertThat(commentRepository.countByPost(postId)).isEqualTo(2);
    }

    @Test
    @DisplayName("分頁 offset 生效")
    void paginatesComments() {
        commentRepository.create(postId, authorId, "A");
        commentRepository.create(postId, authorId, "B");
        commentRepository.create(postId, authorId, "C");

        assertThat(commentRepository.findPageByPost(postId, 2, 0)).extracting(Comment::content)
                .containsExactly("A", "B");
        assertThat(commentRepository.findPageByPost(postId, 2, 2)).extracting(Comment::content)
                .containsExactly("C");
    }

    @Nested
    @DisplayName("留言數與留言必須同進同退")
    class CommentCountConsistency {

        @Test
        @DisplayName("新增留言會在同一筆交易內遞增發文的留言數")
        void incrementsCountOnCreate() {
            commentRepository.create(postId, authorId, "留言");

            assertThat(commentCountOfPost()).isEqualTo(1);
            assertThat(commentRepository.countByPost(postId)).isEqualTo(1);
        }

        @Test
        @DisplayName("刪除留言會在同一筆交易內遞減發文的留言數")
        void decrementsCountOnDelete() {
            long commentId = commentRepository.create(postId, authorId, "留言");

            assertThat(commentRepository.delete(commentId, authorId)).isTrue();

            assertThat(commentCountOfPost()).isZero();
            assertThat(commentRepository.countByPost(postId)).isZero();
        }

        @Test
        @DisplayName("非留言者刪不掉，留言數也不會被誤減")
        void strangerCannotDeleteAndCountStaysIntact() {
            long commentId = commentRepository.create(postId, authorId, "留言");
            long strangerId = userRepository.register(TestData.uniquePhoneNumber(), "路人", null, "hash", "salt");

            assertThat(commentRepository.delete(commentId, strangerId)).isFalse();

            assertThat(commentCountOfPost()).isEqualTo(1);
            assertThat(commentRepository.countByPost(postId)).isEqualTo(1);
        }

        @Test
        @DisplayName("重複刪除同一則留言不會讓計數變成負數")
        void repeatedDeleteDoesNotUnderflowCount() {
            long commentId = commentRepository.create(postId, authorId, "留言");

            assertThat(commentRepository.delete(commentId, authorId)).isTrue();
            assertThat(commentRepository.delete(commentId, authorId)).isFalse();

            assertThat(commentCountOfPost()).isZero();
        }
    }

    @Nested
    @DisplayName("交易回滾")
    class Rollback {

        @Test
        @DisplayName("對不存在的發文留言：外鍵擋下並回滾，不留任何痕跡")
        void rollsBackWhenPostDoesNotExist() {
            long missingPostId = 999_999L;

            assertThatThrownBy(() -> commentRepository.create(missingPostId, authorId, "幽靈留言"))
                    .isInstanceOf(BusinessException.class);

            assertThat(commentRepository.countByPost(missingPostId)).isZero();
        }

        @Test
        @DisplayName("刪除發文會連同其留言一併刪除（跨兩張資料表的交易）")
        void deletingPostAlsoDeletesItsComments() {
            commentRepository.create(postId, authorId, "留言一");
            commentRepository.create(postId, authorId, "留言二");

            assertThat(postRepository.delete(postId, authorId)).isTrue();

            assertThat(postRepository.findById(null, postId)).isEmpty();
            assertThat(commentRepository.countByPost(postId)).isZero();
        }

        /**
         * 這是整組測試裡最關鍵的一個。
         *
         * <p>{@code sp_post_delete} 是「先刪留言、再刪發文」；若刪發文時因為不是本人而影響 0 列，
         * 前面已經刪掉的留言就必須被還原。少了 ROLLBACK 的話，任何人都能藉由對別人的發文
         * 發出刪除請求，把該發文底下的留言全部清空——刪不掉發文，卻毀了留言。
         */
        @Test
        @DisplayName("非本人刪除發文時整筆回滾：發文與其留言都完好如初")
        void restoresCommentsWhenPostDeletionIsRejected() {
            commentRepository.create(postId, authorId, "留言一");
            commentRepository.create(postId, authorId, "留言二");
            long strangerId = userRepository.register(TestData.uniquePhoneNumber(), "路人", null, "hash", "salt");

            assertThat(postRepository.delete(postId, strangerId)).isFalse();

            assertThat(postRepository.findById(null, postId)).isPresent();
            assertThat(commentRepository.countByPost(postId)).isEqualTo(2);
            assertThat(commentCountOfPost()).isEqualTo(2);
        }
    }

    private int commentCountOfPost() {
        return postRepository.findById(null, postId).map(Post::commentCount).orElseThrow();
    }
}
