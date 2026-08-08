package com.esun.social.presentation.controller;

import com.esun.social.business.service.PostService;
import com.esun.social.business.service.TagService;
import com.esun.social.common.response.ApiResponse;
import com.esun.social.common.response.CursorPageResponse;
import com.esun.social.common.security.AuthenticatedUser;
import com.esun.social.presentation.dto.response.PostResponse;
import com.esun.social.presentation.dto.response.TagResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 標籤端點，全部公開。
 *
 * <p>標籤以名稱定位而非數字 ID：名稱本身就是唯一的，而且讓網址可讀（{@code /api/tags/登山/posts}）。
 * 內部主鍵沒有理由外流。
 */
@RestController
@RequestMapping("/api/tags")
@Validated
public class TagController {

    private final TagService tagService;
    private final PostService postService;

    public TagController(TagService tagService, PostService postService) {
        this.tagService = tagService;
        this.postService = postService;
    }

    /** 熱門標籤，依使用次數由多到少。 */
    @GetMapping("/popular")
    public ApiResponse<List<TagResponse>> popular(
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "至少 1 筆")
                    @Max(value = 100, message = "最多 100 筆")
                    int limit) {
        return ApiResponse.success(
                tagService.listPopular(limit).stream().map(TagResponse::from).toList());
    }

    /** 某標籤底下的發文，分頁方式與時間軸相同。 */
    @GetMapping("/{name}/posts")
    public ApiResponse<CursorPageResponse<PostResponse>> postsByTag(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable String name,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每頁至少 1 筆")
                    @Max(value = 100, message = "每頁最多 100 筆")
                    int size) {
        Long viewerId = principal == null ? null : principal.userId();
        return ApiResponse.success(
                postService.listByTag(viewerId, name, cursor, size).map(PostResponse::from));
    }
}
