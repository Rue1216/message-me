package com.esun.social.presentation.dto.response;

import com.esun.social.business.model.Tag;

/**
 * 標籤回應。
 *
 * <p>不回傳 {@code tagId}：對外的標籤識別一律用名稱（{@code /api/tags/{name}/posts}），
 * 因為名稱本身就是唯一的，而且可讀。讓內部主鍵留在內部。
 */
public record TagResponse(String name, int postCount) {

    public static TagResponse from(Tag tag) {
        return new TagResponse(tag.name(), tag.postCount());
    }
}
