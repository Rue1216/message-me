package com.esun.social.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 登入請求。
 *
 * <p>此處刻意不套用格式與長度規則：登入是驗證既有憑證，不是建立新資料。
 * 若在這裡擋掉格式不符的輸入，回應就會與「格式正確但查無此人」不同，
 * 反而成為列舉帳號的線索。一律讓它走完驗證流程並回傳同一個錯誤。
 */
public record LoginRequest(
        @NotBlank(message = "請填寫手機號碼") String phoneNumber, @NotBlank(message = "請填寫密碼") String password) {}
