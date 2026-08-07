package com.esun.social.business.model;

/**
 * 使用者與其密碼憑證，僅供登入驗證使用。
 *
 * <p>對應 {@code sp_user_find_by_phone} —— 唯一會回傳雜湊與鹽的 Stored Procedure。
 * 其他查詢路徑一律只拿到 {@link User}。
 */
public record UserCredentials(User user, String passwordHash, String passwordSalt) {}
