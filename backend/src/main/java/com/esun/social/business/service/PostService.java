package com.esun.social.business.service;

import com.esun.social.business.model.Post;
import com.esun.social.common.exception.BusinessException;
import com.esun.social.common.exception.ErrorCode;
import com.esun.social.common.response.PageResponse;
import com.esun.social.common.util.HtmlSanitizer;
import com.esun.social.common.util.ImagePaths;
import com.esun.social.data.repository.PostRepository;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 發文的業務規則。
 *
 * <h2>關於 {@code @Transactional}</h2>
 * 這裡刻意<strong>不</strong>在方法上加 {@code @Transactional}。跨資料表的異動已經包在
 * {@code sp_post_delete} 自己的 {@code START TRANSACTION / COMMIT} 裡；若再由 Spring 開一層外部交易，
 * SP 內的 COMMIT 會把外層交易一併提交，反而讓交易邊界變得難以推理。
 * Spring 的交易只在「一個業務動作需要橫跨多支各自不帶交易的 SP」時才有必要。
 */
@Service
public class PostService {

    private final PostRepository postRepository;
    private final HtmlSanitizer htmlSanitizer;

    public PostService(PostRepository postRepository, HtmlSanitizer htmlSanitizer) {
        this.postRepository = postRepository;
        this.htmlSanitizer = htmlSanitizer;
    }

    /** 時間軸分頁，新到舊。 */
    public PageResponse<Post> list(int page, int size) {
        long total = postRepository.count();
        int offset = (page - 1) * size;
        // 超出範圍的頁碼回空清單而非錯誤：使用者停在第 5 頁時別人刪了文，重新整理不該噴 404
        List<Post> posts = offset >= total ? List.of() : postRepository.findPage(size, offset);
        return PageResponse.of(posts, page, size, total);
    }

    /**
     * @throws BusinessException 找不到該發文
     */
    public Post findById(long postId) {
        return postRepository
                .findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "找不到這篇發文"));
    }

    public Post create(long userId, String content, String image) {
        long postId = postRepository.create(userId, requireContent(content), ImagePaths.requireUploadedOrNull(image));
        return findById(postId);
    }

    /**
     * 編輯發文，僅限本人。
     *
     * @throws BusinessException 發文不存在（404）或不屬於此使用者（403）
     */
    public Post update(long postId, long userId, String content, String image) {
        requireOwnership(postId, userId);

        boolean updated =
                postRepository.update(postId, userId, requireContent(content), ImagePaths.requireUploadedOrNull(image));
        if (!updated) {
            // 兩次查詢之間發文被刪掉的競態；SP 的 user_id 比對是這條路徑的第二道關卡
            throw new BusinessException(ErrorCode.NOT_FOUND, "找不到這篇發文");
        }
        return findById(postId);
    }

    /**
     * 刪除發文，連同其全部留言，僅限本人。
     *
     * @throws BusinessException 發文不存在（404）或不屬於此使用者（403）
     */
    public void delete(long postId, long userId) {
        requireOwnership(postId, userId);

        if (!postRepository.delete(postId, userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "找不到這篇發文");
        }
    }

    /**
     * 權限檢查刻意分兩層：
     * 這裡先查出發文，才能區分「不存在」（404）與「不是你的」（403）——SP 只回影響筆數，
     * 兩種情形都是 0，光看它無法給出正確的狀態碼。SP 內的 {@code user_id} 比對則保證
     * 即使這層被繞過，也改不到別人的資料。
     */
    private void requireOwnership(long postId, long userId) {
        Post existing = findById(postId);
        if (!existing.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能編輯或刪除自己的發文");
        }
    }

    private String requireContent(String content) {
        String sanitised = htmlSanitizer.sanitize(content);
        if (sanitised == null || sanitised.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "發文內容不可為空");
        }
        return sanitised;
    }
}
