package com.esun.social.presentation.dto.response;

import com.esun.social.business.model.Comment;
import com.esun.social.business.model.Post;

/**
 * 內容作者的顯示資訊，內嵌於發文與留言的回應中。
 *
 * <p>只有畫面上會用到的欄位。作者的手機號碼與電子郵件不在此列——
 * 動態牆是公開的，一則發文若帶出作者的聯絡方式，整個平台的個資就攤開了。
 *
 * @param deleted 作者是否已刪除帳號。此時 {@code userName} 已是匿名字串，
 *                展示層據此決定不要連到個人頁
 */
public record AuthorResponse(long userId, String userName, String coverImage, boolean deleted) {

    public static AuthorResponse from(Post post) {
        return new AuthorResponse(post.userId(), post.authorName(), post.authorCoverImage(), post.authorDeleted());
    }

    public static AuthorResponse from(Comment comment) {
        return new AuthorResponse(
                comment.userId(), comment.authorName(), comment.authorCoverImage(), comment.authorDeleted());
    }
}
