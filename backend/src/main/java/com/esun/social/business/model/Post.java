package com.esun.social.business.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 發文領域模型，含作者的顯示資訊。
 *
 * <p>作者欄位由 {@code v_post_detail} 檢視表在 SQL 中 JOIN users 一次帶出。若改成先查發文再逐筆查作者，
 * 一頁 10 篇就是 11 次查詢；把 JOIN 留在資料層是這個設計的重點之一。標籤同理，
 * 由檢視表以 GROUP_CONCAT 一併帶回，不另外往返。
 *
 * @param commentCount 反正規化的留言數，由 {@code sp_comment_create/delete} 在交易中維護
 * @param likeCount    反正規化的按讚數，由 {@code sp_post_like/unlike} 在交易中維護
 * @param likedByMe    「目前這位觀看者」是否按過讚。它不是發文的固有屬性而是查詢的產物，
 *                     訪客（未登入）一律為 {@code false}
 * @param authorDeleted 作者是否已刪除帳號。發文本身會保留，但展示層需要據此標示
 * @param tags         標籤名稱，已正規化為小寫且不含 {@code #}
 */
public record Post(
        long postId,
        long userId,
        String content,
        String image,
        int commentCount,
        int likeCount,
        boolean likedByMe,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String authorName,
        String authorCoverImage,
        boolean authorDeleted,
        List<String> tags) {

    /** 標籤清單一律為不可變副本，避免呼叫端在共用的模型上就地改動。 */
    public Post {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    /** 這篇發文是否屬於指定使用者。編輯與刪除的權限判斷由此而來。 */
    public boolean isOwnedBy(long candidateUserId) {
        return userId == candidateUserId;
    }
}
