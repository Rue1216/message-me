package com.esun.social.presentation.dto.response;

import com.esun.social.business.model.Comment;
import java.time.LocalDateTime;

/** 留言回應。 */
public record CommentResponse(
        long commentId, long postId, String content, LocalDateTime createdAt, AuthorResponse author) {

    public static CommentResponse from(Comment comment) {
        return new CommentResponse(
                comment.commentId(),
                comment.postId(),
                comment.content(),
                comment.createdAt(),
                new AuthorResponse(comment.userId(), comment.authorName(), comment.authorCoverImage()));
    }
}
