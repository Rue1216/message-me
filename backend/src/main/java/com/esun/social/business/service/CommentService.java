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
        return findById(commentId);
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
     * @throws BusinessException 找不到該留言
     */
    public Comment findById(long commentId) {
        return commentRepository
                .findById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "找不到這則留言"));
    }

    /**
     * 編輯留言，僅限留言者本人。
     *
     * @throws BusinessException 留言不存在（404）或不屬於此使用者（403）
     */
    public Comment update(long commentId, long userId, String content) {
        requireOwnership(commentId, userId);

        if (!commentRepository.update(commentId, userId, requireContent(content))) {
            // 兩次查詢之間留言被刪掉的競態；SP 的 user_id 比對是這條路徑的第二道關卡
            throw new BusinessException(ErrorCode.NOT_FOUND, "找不到這則留言");
        }
        return findById(commentId);
    }

    /**
     * 刪除留言，僅限留言者本人。
     *
     * @throws BusinessException 留言不存在（404）或不屬於此使用者（403）
     */
    public void delete(long commentId, long userId) {
        requireOwnership(commentId, userId);

        if (!commentRepository.delete(commentId, userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "找不到這則留言");
        }
    }

    /**
     * 權限檢查的兩層結構與發文相同：先查出留言才能區分 404 與 403，
     * SP 內的 {@code user_id} 比對則保證即使這層被繞過也改不到別人的資料。
     *
     * <p>對外揭露「這則留言存在但不是你的」不構成資訊洩漏：留言本來就是公開可讀的，
     * 任何人打開該篇發文都看得到它。
     */
    private void requireOwnership(long commentId, long userId) {
        if (!findById(commentId).isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能編輯或刪除自己的留言");
        }
    }

    private void requirePostExists(long postId) {
        // 這裡不在意觀看者是誰，只是確認發文存在，因此 viewerId 傳 null
        if (postRepository.findById(null, postId).isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "找不到這篇發文");
        }
    }

    private String requireContent(String content) {
        String sanitised = htmlSanitizer.sanitize(content);
        if (sanitised == null || sanitised.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "留言內容不可為空");
        }
        return sanitised;
    }
}
