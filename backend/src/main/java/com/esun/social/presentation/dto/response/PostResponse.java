package com.esun.social.presentation.dto.response;

import com.esun.social.business.model.Post;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 發文回應。
 *
 * @param likedByMe 目前這位請求者是否按過讚。未登入時恆為 {@code false}
 * @param tags      標籤名稱，不含 {@code #}
 */
public record PostResponse(
        long postId,
        String content,
        String image,
        int commentCount,
        int likeCount,
        boolean likedByMe,
        List<String> tags,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        AuthorResponse author) {

    public static PostResponse from(Post post) {
        return new PostResponse(
                post.postId(),
                post.content(),
                post.image(),
                post.commentCount(),
                post.likeCount(),
                post.likedByMe(),
                post.tags(),
                post.createdAt(),
                post.updatedAt(),
                AuthorResponse.from(post));
    }
}
