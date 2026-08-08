package com.esun.social.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改密碼的請求。
 *
 * <p>必須提供目前的密碼：權杖可能外洩，若只憑權杖就能改密碼，攻擊者能直接接管帳號。
 * 要求舊密碼讓「持有權杖」與「知道密碼」成為兩道獨立的關卡。
 *
 * <p>長度限制與註冊時一致（見 {@code RegisterRequest}）——同一條規則出現兩次是為了讓
 * 每個端點都能獨立驗證，而不是靠呼叫順序。
 */
public record ChangePasswordRequest(
        @NotBlank(message = "請填寫目前的密碼") String currentPassword,
        @NotBlank(message = "請填寫新密碼")
                @Size(min = 8, max = 100, message = "密碼長度需介於 8 至 100 字元")
                String newPassword) {}
