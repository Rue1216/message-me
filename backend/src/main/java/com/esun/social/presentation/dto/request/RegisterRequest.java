package com.esun.social.presentation.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 註冊請求。
 *
 * @param phoneNumber 手機號碼，同時是登入帳號
 * @param userName    顯示名稱
 * @param password    明文密碼，僅存在於這一次請求中，不會被記錄
 * @param email       電子郵件，選填
 */
public record RegisterRequest(
        @NotBlank(message = "請填寫手機號碼")
                @Pattern(regexp = "^09\\d{8}$", message = "手機號碼格式應為 09 開頭的 10 位數字")
                String phoneNumber,
        @NotBlank(message = "請填寫使用者名稱") @Size(max = 50, message = "使用者名稱不可超過 50 字") String userName,
        @NotBlank(message = "請填寫密碼") @Size(min = 8, max = 100, message = "密碼長度需介於 8 至 100 字元") String password,
        @Email(message = "電子郵件格式不正確") @Size(max = 255, message = "電子郵件不可超過 255 字") String email) {}
