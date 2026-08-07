package com.esun.social.presentation.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 更新個人檔案的請求。
 *
 * <p>全欄位取代語意（HTTP PUT）：沒給的欄位就是要清空，而不是維持原值。
 * 手機號碼不在此列——它是登入帳號，變更帳號需要另一套驗證流程（例如簡訊驗證），
 * 不該混在個人檔案編輯裡。
 */
public record UpdateProfileRequest(
        @NotBlank(message = "請填寫使用者名稱") @Size(max = 50, message = "使用者名稱不可超過 50 字") String userName,
        @Email(message = "電子郵件格式不正確") @Size(max = 255, message = "電子郵件不可超過 255 字") String email,
        @Size(max = 500, message = "自我介紹不可超過 500 字") String biography,
        @Size(max = 500, message = "圖片路徑過長") String coverImage) {}
