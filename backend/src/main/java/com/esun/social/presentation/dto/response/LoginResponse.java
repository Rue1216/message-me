package com.esun.social.presentation.dto.response;

import com.esun.social.business.model.AuthToken;

/**
 * 登入回應。
 *
 * @param accessToken JWT，前端後續以 {@code Authorization: Bearer <accessToken>} 附加
 * @param tokenType   固定為 {@code Bearer}，讓前端不必寫死這個字串
 * @param expiresIn   有效秒數
 * @param user        登入者的個人檔案，省去登入後立刻再打一次 /api/users/me
 */
public record LoginResponse(String accessToken, String tokenType, long expiresIn, CurrentUserResponse user) {

    public static LoginResponse from(AuthToken token) {
        return new LoginResponse(
                token.accessToken(),
                "Bearer",
                token.expiresInSeconds(),
                CurrentUserResponse.from(token.user()));
    }
}
