package com.esun.social.presentation.dto.response;

import com.esun.social.business.model.User;
import java.time.LocalDateTime;

/**
 * 本人的完整個人檔案，含手機號碼與電子郵件。
 *
 * <p>只在 {@code GET /api/users/me} 與註冊／登入的回應中出現。
 */
public record CurrentUserResponse(
        long userId,
        String phoneNumber,
        String userName,
        String email,
        String coverImage,
        String biography,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static CurrentUserResponse from(User user) {
        return new CurrentUserResponse(
                user.userId(),
                user.phoneNumber(),
                user.userName(),
                user.email(),
                user.coverImage(),
                user.biography(),
                user.createdAt(),
                user.updatedAt());
    }
}
