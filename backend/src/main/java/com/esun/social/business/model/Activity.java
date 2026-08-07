package com.esun.social.business.model;

import java.time.LocalDateTime;

/**
 * 個人頁時間軸上的一筆活動 —— 一則發文，或一則留言。
 *
 * <p>兩種活動由 {@code sp_user_activity_list} 以 UNION ALL 併成單一時間軸。
 * 這是刻意的「寬型別」：同一列同時容納兩種形狀，不足的欄位以空值表示。
 * 替代方案是分別查兩次再於應用層合併排序，但那樣就無法正確分頁
 * ——分頁必須發生在合併之後，否則兩邊各取 10 筆合起來未必是全域的前 10 筆。
 *
 * @param activityId    發文為 {@code postId}，留言為 {@code commentId}
 * @param postId        兩種活動都指向一則發文：發文是自己，留言是所屬的那一則
 * @param content       發文或留言的內容
 * @param image         僅發文可能有值
 * @param commentCount  僅發文有意義，留言恆為 0
 * @param likeCount     同上
 * @param postExcerpt   僅留言有值：被留言的那則發文的內容摘要（前 200 字）
 * @param postAuthorName 僅留言有值：被留言的那則發文的作者名稱
 */
public record Activity(
        ActivityType type,
        long activityId,
        long postId,
        String content,
        String image,
        int commentCount,
        int likeCount,
        LocalDateTime createdAt,
        String postExcerpt,
        String postAuthorName) {

    /** 活動的種類。名稱與 {@code sp_user_activity_list} 回傳的 {@code activity_type} 字串一致。 */
    public enum ActivityType {
        POST,
        COMMENT
    }
}
