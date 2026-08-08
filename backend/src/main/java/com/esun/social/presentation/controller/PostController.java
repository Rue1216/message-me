package com.esun.social.presentation.controller;

import com.esun.social.business.model.Post;
import com.esun.social.business.service.PostService;
import com.esun.social.common.response.ApiResponse;
import com.esun.social.common.response.CursorPageResponse;
import com.esun.social.common.security.AuthenticatedUser;
import com.esun.social.presentation.dto.request.PostRequest;
import com.esun.social.presentation.dto.response.PostResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
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
 * <p>讀取（列表、單篇、搜尋）公開；新增、編輯、刪除、按讚需要登入。
 * 作者是誰一律取自權杖，不從請求主體讀——否則等於讓呼叫端自己宣稱身分。
 *
 * <p>公開端點仍會接收 {@code @AuthenticationPrincipal}：訪客時它是 {@code null}，
 * 已登入時則用來判斷每則發文的 {@code likedByMe}。這是「公開讀取但內容因人而異」的必要參數，
 * 與授權無關。
 */
@RestController
@RequestMapping("/api/posts")
@Validated
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    /**
     * 時間軸，新到舊。
     *
     * <p>採游標分頁：{@code cursor} 留空即為第一頁，之後把上一次回應的 {@code nextCursor}
     * 原樣帶回來。游標對呼叫端不透明，不應解析它的內容。
     */
    @GetMapping
    public ApiResponse<CursorPageResponse<PostResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每頁至少 1 筆")
                    @Max(value = 100, message = "每頁最多 100 筆")
                    int size) {
        return ApiResponse.success(
                postService.list(viewerId(principal), cursor, size).map(PostResponse::from));
    }

    /**
     * 關鍵字搜尋，分頁方式與時間軸相同。
     *
     * <p>路徑必須宣告在 {@code /{postId}} 之前才不會被它吃掉——Spring MVC 對字面路徑的
     * 優先權高於路徑變數，因此這裡的順序其實不影響對應結果，但擺在前面能讓讀的人少一次疑惑。
     */
    @GetMapping("/search")
    public ApiResponse<CursorPageResponse<PostResponse>> search(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam @Size(max = 100, message = "搜尋關鍵字不可超過 100 字") String q,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每頁至少 1 筆")
                    @Max(value = 100, message = "每頁最多 100 筆")
                    int size) {
        return ApiResponse.success(
                postService.search(viewerId(principal), q, cursor, size).map(PostResponse::from));
    }

    @GetMapping("/{postId}")
    public ApiResponse<PostResponse> findById(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable long postId) {
        return ApiResponse.success(PostResponse.from(postService.findById(viewerId(principal), postId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PostResponse>> create(
            @AuthenticationPrincipal AuthenticatedUser principal, @Valid @RequestBody PostRequest request) {
        Post created = postService.create(principal.userId(), request.content(), request.image(), request.tags());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(PostResponse.from(created)));
    }

    @PutMapping("/{postId}")
    public ApiResponse<PostResponse> update(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable long postId,
            @Valid @RequestBody PostRequest request) {
        Post updated =
                postService.update(postId, principal.userId(), request.content(), request.image(), request.tags());
        return ApiResponse.success(PostResponse.from(updated));
    }

    @DeleteMapping("/{postId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable long postId) {
        postService.delete(postId, principal.userId());
        return ApiResponse.noContent();
    }

    /**
     * 按讚。
     *
     * <p>把「讚」設計成 {@code /posts/{id}/likes} 這個子資源，而不是 {@code POST /posts/{id}/like}
     * 這樣的動詞端點：按讚是建立一筆關聯、取消是刪除它，用 POST / DELETE 表達最貼近實際語意。
     *
     * <p>兩者都是冪等的（見 {@code sp_post_like}），重複呼叫不會讓計數失準，
     * 前端的樂觀更新因此可以安心重試。
     */
    @PostMapping("/{postId}/likes")
    public ApiResponse<PostResponse> like(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable long postId) {
        return ApiResponse.success(PostResponse.from(postService.like(postId, principal.userId())));
    }

    @DeleteMapping("/{postId}/likes")
    public ApiResponse<PostResponse> unlike(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable long postId) {
        return ApiResponse.success(PostResponse.from(postService.unlike(postId, principal.userId())));
    }

    /** 訪客為 {@code null}；用來決定 {@code likedByMe}，不參與授權判斷。 */
    private static Long viewerId(AuthenticatedUser principal) {
        return principal == null ? null : principal.userId();
    }
}
