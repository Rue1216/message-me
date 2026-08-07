package com.esun.social.business.model;

import java.time.LocalDateTime;

/**
 * 留言領域模型，含留言者的顯示資訊。
 *
 * <p>沒有 {@code updatedAt}：留言不提供編輯功能，資料表也沒有這個欄位。
 */
public record Comment(
        long commentId,
        long postId,
        long userId,
        String content,
        LocalDateTime createdAt,
        String authorName,
        String authorCoverImage) {}
