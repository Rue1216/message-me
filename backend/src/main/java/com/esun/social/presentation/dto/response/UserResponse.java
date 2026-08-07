package com.esun.social.presentation.dto.response;

import com.esun.social.business.model.User;
import java.time.LocalDateTime;

/**
 * 公開的使用者檔案。
 *
 * <p><strong>不含手機號碼與電子郵件。</strong>{@code GET /api/users/{userId}} 不需要登入即可存取，
 * 若把手機號碼放進來，整個平台的使用者聯絡方式就能被逐一枚舉出來。
 * 需要這些欄位的只有本人，走 {@link CurrentUserResponse}。
 *
 * @param deleted 帳號是否已刪除。已刪除的帳號仍可查詢——它的發文與留言還留在別人的討論串中，
 *                點進作者頁時應該看到一個「已刪除的使用者」而不是 404
 */
public record UserResponse(
        long userId,
        String userName,
        String coverImage,
        String biography,
        boolean deleted,
        LocalDateTime createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.userId(),
                user.userName(),
                user.coverImage(),
                user.biography(),
                user.isDeleted(),
                user.createdAt());
    }
}
