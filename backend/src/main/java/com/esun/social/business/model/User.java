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
 *
 * @param deletedAt 軟刪除時間；非 {@code null} 代表帳號已刪除。此時 {@code userName} 已被
 *                  {@code sp_user_soft_delete} 抹為匿名字串，其餘身分欄位皆已清空
 */
public record User(
        long userId,
        String phoneNumber,
        String userName,
        String email,
        String coverImage,
        String biography,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime deletedAt) {

    /** 帳號是否已刪除。展示層據此標示，登入路徑則由 SP 的 WHERE 條件直接擋下。 */
    public boolean isDeleted() {
        return deletedAt != null;
    }
}
