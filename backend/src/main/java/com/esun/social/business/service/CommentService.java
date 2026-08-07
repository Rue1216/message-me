package com.esun.social.business.service;

import com.esun.social.business.model.Comment;
import com.esun.social.common.exception.BusinessException;
import com.esun.social.common.exception.ErrorCode;
import com.esun.social.common.response.PageResponse;
import com.esun.social.common.util.HtmlSanitizer;
import com.esun.social.data.repository.CommentRepository;
import com.esun.social.data.repository.PostRepository;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 留言的業務規則。
 *
 * <p>與發文一樣不加 {@code @Transactional}：{@code sp_comment_create} 與
 * {@code sp_comment_delete} 各自在 SP 內以 {@code START TRANSACTION / COMMIT} 包住
 * 「留言異動 + 發文留言數更新」，外面再包一層只會讓交易邊界重疊。
 */
@Service
public class CommentService {

    /** 新增留言後回頭尋找它時，往回撈的筆數。 */
    private static final int TAIL_WINDOW_SIZE = 20;

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final HtmlSanitizer htmlSanitizer;

    public CommentService(
            CommentRepository commentRepository, PostRepository postRepository, HtmlSanitizer htmlSanitizer) {
        this.commentRepository = commentRepository;
        this.postRepository = postRepository;
        this.htmlSanitizer = htmlSanitizer;
    }

    /**
     * 新增留言。
     *
     * <p>先確認發文存在，是為了回傳明確的 404 而不是讓外鍵錯誤冒上來。
     * 兩者之間的競態仍由外鍵擋住（見 {@code CommentRepository#create}），
     * 這層檢查是為了訊息品質，不是為了正確性。
     *
     * @throws BusinessException 發文不存在，或內容清洗後為空
     */
    public Comment create(long postId, long userId, String content) {
        requirePostExists(postId);

        long commentId = commentRepository.create(postId, userId, requireContent(content));
        return findInPost(postId, commentId);
    }

    /**
     * 單篇發文的留言分頁，舊到新。
     *
     * @throws BusinessException 發文不存在
     */
    public PageResponse<Comment> listByPost(long postId, int page, int size) {
        requirePostExists(postId);

        long total = commentRepository.countByPost(postId);
        int offset = (page - 1) * size;
        List<Comment> comments = offset >= total ? List.of() : commentRepository.findPageByPost(postId, size, offset);
        return PageResponse.of(comments, page, size, total);
    }

    /**
     * 刪除留言，僅限留言者本人。
     *
     * <p>留言沒有單筆查詢的 Stored Procedure，因此無法像發文那樣區分「不存在」與「不是你的」——
     * 兩種情形一律回 404。這對呼叫端反而更保守：外人無從得知某則留言是否存在。
     *
     * @throws BusinessException 留言不存在或不屬於此使用者
     */
    public void delete(long commentId, long userId) {
        if (!commentRepository.delete(commentId, userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "找不到這則留言");
        }
    }

    private void requirePostExists(long postId) {
        if (postRepository.findById(postId).isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "找不到這篇發文");
        }
    }

    /**
     * 新增後把剛寫入的那則撈回來，讓回應帶有資料庫產生的時間與 JOIN 出來的作者資訊。
     *
     * <p>留言依時間由舊到新排列，新增的那則必定落在最後。這裡取末端一小段而非只取最後一筆，
     * 是為了容忍同一瞬間有別人也在留言的情況——只取一筆的話，別人的留言會把它擠掉。
     */
    private Comment findInPost(long postId, long commentId) {
        long total = commentRepository.countByPost(postId);
        int offset = (int) Math.max(0, total - TAIL_WINDOW_SIZE);
        return commentRepository.findPageByPost(postId, TAIL_WINDOW_SIZE, offset).stream()
                .filter(comment -> comment.commentId() == commentId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("剛新增的留言 " + commentId + " 查不到"));
    }

    private String requireContent(String content) {
        String sanitised = htmlSanitizer.sanitize(content);
        if (sanitised == null || sanitised.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "留言內容不可為空");
        }
        return sanitised;
    }
}
