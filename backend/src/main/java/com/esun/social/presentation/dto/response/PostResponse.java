package com.esun.social.presentation.dto.response;

import com.esun.social.business.model.Post;
import java.time.LocalDateTime;

/** 發文回應。 */
public record PostResponse(
        long postId,
        String content,
        String image,
        int commentCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        AuthorResponse author) {

    public static PostResponse from(Post post) {
        return new PostResponse(
                post.postId(),
                post.content(),
                post.image(),
                post.commentCount(),
                post.createdAt(),
                post.updatedAt(),
                new AuthorResponse(post.userId(), post.authorName(), post.authorCoverImage()));
    }
}
