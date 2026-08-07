package com.esun.social.presentation.dto.response;

import com.esun.social.business.model.Activity;
import java.time.LocalDateTime;

/**
 * 個人頁時間軸上的一筆活動。
 *
 * @param type           {@code POST} 或 {@code COMMENT}，前端據此決定要怎麼呈現這一列
 * @param postId         兩種活動都指向一則發文，因此永遠可以連過去
 * @param commentCount   僅 {@code POST} 有意義
 * @param likeCount      僅 {@code POST} 有意義
 * @param postExcerpt    僅 {@code COMMENT} 有值：被留言的那則發文的內容摘要
 * @param postAuthorName 僅 {@code COMMENT} 有值：被留言的那則發文的作者
 */
public record ActivityResponse(
        String type,
        long activityId,
        long postId,
        String content,
        String image,
        int commentCount,
        int likeCount,
        LocalDateTime createdAt,
        String postExcerpt,
        String postAuthorName) {

    public static ActivityResponse from(Activity activity) {
        return new ActivityResponse(
                activity.type().name(),
                activity.activityId(),
                activity.postId(),
                activity.content(),
                activity.image(),
                activity.commentCount(),
                activity.likeCount(),
                activity.createdAt(),
                activity.postExcerpt(),
                activity.postAuthorName());
    }
}
