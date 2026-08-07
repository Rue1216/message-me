package com.esun.social.presentation.dto.response;

import com.esun.social.business.model.User;
import java.time.LocalDateTime;

/**
 * 公開的使用者檔案。
 *
 * <p><strong>不含手機號碼與電子郵件。</strong>{@code GET /api/users/{userId}} 不需要登入即可存取，
 * 若把手機號碼放進來，整個平台的使用者聯絡方式就能被逐一枚舉出來。
 * 需要這些欄位的只有本人，走 {@link CurrentUserResponse}。
 */
public record UserResponse(
        long userId, String userName, String coverImage, String biography, LocalDateTime createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.userId(), user.userName(), user.coverImage(), user.biography(), user.createdAt());
    }
}
