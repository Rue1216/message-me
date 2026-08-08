package com.esun.social.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 刪除帳號的請求。
 *
 * <p>要求密碼的理由與修改密碼相同，而且更強：這是不可逆的操作，
 * 只憑一個可能已外洩的權杖就執行，代價太高。
 */
public record DeleteAccountRequest(@NotBlank(message = "請填寫密碼以確認身分") String password) {}
