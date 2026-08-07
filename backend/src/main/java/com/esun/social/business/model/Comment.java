package com.esun.social.business.model;

import java.time.LocalDateTime;

/**
 * 留言領域模型，含留言者的顯示資訊。
 *
 * @param updatedAt     留言可由作者編輯；與 {@code createdAt} 相異即代表編輯過
 * @param authorDeleted 留言者是否已刪除帳號。留言會保留，但展示層需要據此標示
 */
public record Comment(
        long commentId,
        long postId,
        long userId,
        String content,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String authorName,
        String authorCoverImage,
        boolean authorDeleted) {

    /** 這則留言是否屬於指定使用者。編輯與刪除的權限判斷由此而來。 */
    public boolean isOwnedBy(long candidateUserId) {
        return userId == candidateUserId;
    }
}
