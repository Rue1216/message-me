package com.esun.social.business.model;

/**
 * 標籤領域模型。
 *
 * @param name      標籤名稱，已正規化為小寫且不含 {@code #}（見 {@code TagNormalizer}）
 * @param postCount 反正規化的使用次數，由 {@code sp_post_create/update/delete} 在交易中維護
 */
public record Tag(long tagId, String name, int postCount) {}
