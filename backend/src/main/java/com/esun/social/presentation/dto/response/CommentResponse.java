package com.esun.social.presentation.dto.response;

import com.esun.social.business.model.Comment;
import java.time.LocalDateTime;

/**
 * 留言回應。
 *
 * @param updatedAt 與 {@code createdAt} 相異即代表被編輯過，展示層據此標示「已編輯」
 */
public record CommentResponse(
        long commentId,
        long postId,
        String content,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        AuthorResponse author) {

    public static CommentResponse from(Comment comment) {
        return new CommentResponse(
                comment.commentId(),
                comment.postId(),
                comment.content(),
                comment.createdAt(),
                comment.updatedAt(),
                AuthorResponse.from(comment));
    }
}
