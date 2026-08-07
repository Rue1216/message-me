package com.esun.social.presentation.controller;

import com.esun.social.business.model.Post;
import com.esun.social.business.service.PostService;
import com.esun.social.common.response.ApiResponse;
import com.esun.social.common.response.PageResponse;
import com.esun.social.common.security.AuthenticatedUser;
import com.esun.social.presentation.dto.request.PostRequest;
import com.esun.social.presentation.dto.response.PostResponse;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 發文端點。
 *
 * <p>讀取（列表、單篇）公開；新增、編輯、刪除需要登入，且只能操作自己的發文。
 * 作者是誰一律取自權杖，不從請求主體讀——否則等於讓呼叫端自己宣稱身分。
 */
@RestController
@RequestMapping("/api/posts")
@Validated
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    /** 時間軸分頁。頁碼自 1 起算，單頁上限 100 筆。 */
    @GetMapping
    public ApiResponse<PageResponse<PostResponse>> list(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "頁碼自 1 起算") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每頁至少 1 筆")
                    @Max(value = 100, message = "每頁最多 100 筆")
                    int size) {
        return ApiResponse.success(postService.list(page, size).map(PostResponse::from));
    }

    @GetMapping("/{postId}")
    public ApiResponse<PostResponse> findById(@PathVariable long postId) {
        return ApiResponse.success(PostResponse.from(postService.findById(postId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PostResponse>> create(
            @AuthenticationPrincipal AuthenticatedUser principal, @Valid @RequestBody PostRequest request) {
        Post created = postService.create(principal.userId(), request.content(), request.image());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(PostResponse.from(created)));
    }

    @PutMapping("/{postId}")
    public ApiResponse<PostResponse> update(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable long postId,
            @Valid @RequestBody PostRequest request) {
        Post updated = postService.update(postId, principal.userId(), request.content(), request.image());
        return ApiResponse.success(PostResponse.from(updated));
    }

    @DeleteMapping("/{postId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable long postId) {
        postService.delete(postId, principal.userId());
        return ApiResponse.noContent();
    }
}
