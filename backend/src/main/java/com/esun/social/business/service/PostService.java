package com.esun.social.business.service;

import com.esun.social.business.model.Post;
import com.esun.social.common.exception.BusinessException;
import com.esun.social.common.exception.ErrorCode;
import com.esun.social.common.response.CursorPageResponse;
import com.esun.social.common.util.Cursor;
import com.esun.social.common.util.HtmlSanitizer;
import com.esun.social.common.util.ImagePaths;
import com.esun.social.common.util.TagNormalizer;
import com.esun.social.data.repository.PostRepository;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

/**
 * 發文的業務規則。
 *
 * <h2>關於 {@code @Transactional}</h2>
 * 這裡刻意<strong>不</strong>在方法上加 {@code @Transactional}。跨資料表的異動已經包在
 * {@code sp_post_create / update / delete / like / unlike} 各自的 {@code START TRANSACTION / COMMIT} 裡；
 * 若再由 Spring 開一層外部交易，SP 內的 COMMIT 會把外層交易一併提交，反而讓交易邊界變得難以推理。
 * Spring 的交易只在「一個業務動作需要橫跨多支各自不帶交易的 SP」時才有必要——本專案沒有這種情形，
 * 因為每一個跨表動作都被刻意收斂成單一支 SP。
 */
@Service
public class PostService {

    private final PostRepository postRepository;
    private final HtmlSanitizer htmlSanitizer;
    private final TagNormalizer tagNormalizer;

    public PostService(PostRepository postRepository, HtmlSanitizer htmlSanitizer, TagNormalizer tagNormalizer) {
        this.postRepository = postRepository;
        this.htmlSanitizer = htmlSanitizer;
        this.tagNormalizer = tagNormalizer;
    }

    /**
     * 時間軸，新到舊。
     *
     * @param viewerId 觀看者，未登入時為 {@code null}（影響 {@code likedByMe}）
     * @param cursor   上一頁的結尾位置，第一頁時為 {@code null} 或空字串
     */
    public CursorPageResponse<Post> list(Long viewerId, String cursor, int size) {
        Cursor.Position position = Cursor.decode(cursor);
        return toCursorPage(postRepository.findPageByCursor(viewerId, position, size + 1), size);
    }

    /**
     * 關鍵字搜尋。
     *
     * <p>關鍵字同樣經過清洗：它會原樣回到搜尋結果頁供高亮顯示，
     * 是一條會把使用者輸入送回畫面的路徑，不能跳過 XSS 的輸入端防線。
     */
    public CursorPageResponse<Post> search(Long viewerId, String keyword, String cursor, int size) {
        String safeKeyword = htmlSanitizer.sanitize(keyword);
        if (safeKeyword == null || safeKeyword.isBlank()) {
            return CursorPageResponse.last(List.of());
        }
        Cursor.Position position = Cursor.decode(cursor);
        return toCursorPage(postRepository.searchByCursor(viewerId, safeKeyword, position, size + 1), size);
    }

    /** 依標籤列出發文。標籤名稱正規化方式與寫入時一致，否則大小寫不同就查不到。 */
    public CursorPageResponse<Post> listByTag(Long viewerId, String tagName, String cursor, int size) {
        if (tagName == null || tagName.isBlank()) {
            return CursorPageResponse.last(List.of());
        }
        Cursor.Position position = Cursor.decode(cursor);
        String normalisedTag = tagName.trim().toLowerCase(Locale.ROOT);
        return toCursorPage(postRepository.findPageByTag(viewerId, normalisedTag, position, size + 1), size);
    }

    /**
     * @throws BusinessException 找不到該發文
     */
    public Post findById(Long viewerId, long postId) {
        return postRepository
                .findById(viewerId, postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "找不到這篇發文"));
    }

    /**
     * @param tags 使用者指定的標籤；內容中的 {@code #} 不再具有任何意義
     */
    public Post create(long userId, String content, String image, List<String> tags) {
        String safeContent = requireContent(content);
        long postId = postRepository.create(
                userId, safeContent, ImagePaths.requireUploadedOrNull(image), tagNormalizer.normalise(tags));
        return findById(userId, postId);
    }

    /**
     * 編輯發文，僅限本人。標籤以傳入的清單整組替換。
     *
     * @throws BusinessException 發文不存在（404）、不屬於此使用者（403）或標籤不合法（400）
     */
    public Post update(long postId, long userId, String content, String image, List<String> tags) {
        requireOwnership(postId, userId);

        String safeContent = requireContent(content);
        boolean updated = postRepository.update(
                postId,
                userId,
                safeContent,
                ImagePaths.requireUploadedOrNull(image),
                tagNormalizer.normalise(tags));
        if (!updated) {
            // 兩次查詢之間發文被刪掉的競態；SP 的 user_id 比對是這條路徑的第二道關卡
            throw new BusinessException(ErrorCode.NOT_FOUND, "找不到這篇發文");
        }
        return findById(userId, postId);
    }

    /**
     * 刪除發文，連同其全部留言、按讚與標籤關聯，僅限本人。
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
     * 按讚。冪等：重複按讚不會使計數增加，前端的樂觀更新因此可以安心重試。
     *
     * @return 這篇發文按讚後的最新狀態
     * @throws BusinessException 發文不存在（404）
     */
    public Post like(long postId, long userId) {
        postRepository.like(postId, userId).orElseThrow(() -> notFound());
        return findById(userId, postId);
    }

    /**
     * 取消按讚。冪等：未按過讚時不會使計數減少。
     *
     * @return 這篇發文取消後的最新狀態
     * @throws BusinessException 發文不存在（404）
     */
    public Post unlike(long postId, long userId) {
        postRepository.unlike(postId, userId).orElseThrow(() -> notFound());
        return findById(userId, postId);
    }

    /**
     * 把「多取一筆」的查詢結果整理成一頁。
     *
     * <p>資料層一律取 {@code size + 1} 筆：若真的拿到這麼多，代表後面還有資料，
     * 於是裁掉最後一筆並以本頁最後一筆的位置作為下一頁的游標。
     * 這讓「是否還有下一頁」不需要額外的 COUNT 查詢。
     */
    private CursorPageResponse<Post> toCursorPage(List<Post> fetched, int size) {
        boolean hasMore = fetched.size() > size;
        List<Post> items = hasMore ? fetched.subList(0, size) : fetched;
        if (items.isEmpty()) {
            return CursorPageResponse.last(items);
        }
        Post last = items.get(items.size() - 1);
        return CursorPageResponse.of(items, Cursor.encode(last.createdAt(), last.postId()), hasMore);
    }

    /**
     * 權限檢查刻意分兩層：
     * 這裡先查出發文，才能區分「不存在」（404）與「不是你的」（403）——SP 只回影響筆數，
     * 兩種情形都是 0，光看它無法給出正確的狀態碼。SP 內的 {@code user_id} 比對則保證
     * 即使這層被繞過，也改不到別人的資料。
     */
    private void requireOwnership(long postId, long userId) {
        Post existing = findById(userId, postId);
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

    private BusinessException notFound() {
        return new BusinessException(ErrorCode.NOT_FOUND, "找不到這篇發文");
    }
}
