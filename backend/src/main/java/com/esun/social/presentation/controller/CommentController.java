package com.esun.social.presentation.controller;

import com.esun.social.business.model.Comment;
import com.esun.social.business.service.CommentService;
import com.esun.social.common.response.ApiResponse;
import com.esun.social.common.response.PageResponse;
import com.esun.social.common.security.AuthenticatedUser;
import com.esun.social.presentation.dto.request.CommentRequest;
import com.esun.social.presentation.dto.response.CommentResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 留言端點。
 *
 * <p>路徑刻意分成兩種形狀：新增與列出掛在 {@code /api/posts/{postId}/comments} 之下，
 * 因為留言離開發文就沒有意義；刪除則走 {@code /api/comments/{commentId}}，
 * 因為留言 ID 已足以定位，要求呼叫端同時提供發文 ID 只會多一個對不上就出錯的機會。
 */
@RestController
@Validated
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    /** 單篇發文的留言分頁，舊到新。公開。 */
    @GetMapping("/api/posts/{postId}/comments")
    public ApiResponse<PageResponse<CommentResponse>> listByPost(
            @PathVariable long postId,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "頁碼自 1 起算") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "每頁至少 1 筆")
                    @Max(value = 100, message = "每頁最多 100 筆")
                    int size) {
        return ApiResponse.success(commentService.listByPost(postId, page, size).map(CommentResponse::from));
    }

    @PostMapping("/api/posts/{postId}/comments")
    public ResponseEntity<ApiResponse<CommentResponse>> create(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable long postId,
            @Valid @RequestBody CommentRequest request) {
        Comment created = commentService.create(postId, principal.userId(), request.content());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(CommentResponse.from(created)));
    }

    /** 編輯留言，僅限本人。與刪除同理只需要留言 ID。 */
    @PutMapping("/api/comments/{commentId}")
    public ApiResponse<CommentResponse> update(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable long commentId,
            @Valid @RequestBody CommentRequest request) {
        Comment updated = commentService.update(commentId, principal.userId(), request.content());
        return ApiResponse.success(CommentResponse.from(updated));
    }

    @DeleteMapping("/api/comments/{commentId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable long commentId) {
        commentService.delete(commentId, principal.userId());
        return ApiResponse.noContent();
    }
}
