package com.esun.social.common.security;

/**
 * 已通過驗證的使用者，作為 Spring Security 的 principal。
 *
 * <p>只帶 JWT 內含的資訊。任何需要更多欄位的情境都應該回資料庫查，
 * 而不是把個人檔案塞進權杖——權杖簽發後就凍結了，塞得越多越容易與真實狀態不一致。
 *
 * @param userId      使用者 ID
 * @param phoneNumber 手機號碼（登入帳號）
 */
public record AuthenticatedUser(long userId, String phoneNumber) {}
