package com.esun.social.business.model;

import java.time.LocalDateTime;

/**
 * 發文領域模型，含作者的顯示資訊。
 *
 * <p>作者欄位由 {@code sp_post_*} 在 SQL 中 JOIN users 一次帶出。若改成先查發文再逐筆查作者，
 * 一頁 10 篇就是 11 次查詢；把 JOIN 留在 Stored Procedure 裡是這個設計的重點之一。
 *
 * @param commentCount 反正規化的留言數，由 {@code sp_comment_create/delete} 在交易中維護
 */
public record Post(
        long postId,
        long userId,
        String content,
        String image,
        int commentCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String authorName,
        String authorCoverImage) {

    /** 這篇發文是否屬於指定使用者。編輯與刪除的權限判斷由此而來。 */
    public boolean isOwnedBy(long candidateUserId) {
        return userId == candidateUserId;
    }
}
