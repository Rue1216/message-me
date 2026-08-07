package com.esun.social.business.model;

import java.time.LocalDateTime;

/**
 * 使用者領域模型。
 *
 * <p>刻意<strong>不含 {@code passwordHash} 與 {@code passwordSalt}</strong>：
 * 憑證只在登入驗證的那條路徑上出現（見 {@link UserCredentials}），
 * 其餘地方連拿都拿不到，也就不可能不小心序列化出去。
 *
 * <p>時間欄位使用 {@link LocalDateTime}：資料庫的 DATETIME 不帶時區，
 * 兩個容器的 TZ 都固定為 Asia/Taipei，用不帶時區的型別直接對應可避免多餘的轉換。
 */
public record User(
        long userId,
        String phoneNumber,
        String userName,
        String email,
        String coverImage,
        String biography,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
