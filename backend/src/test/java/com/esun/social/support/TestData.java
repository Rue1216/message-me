package com.esun.social.support;

import com.esun.social.business.model.Comment;
import com.esun.social.business.model.Post;
import com.esun.social.business.model.User;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 測試用的資料產生器。
 *
 * <p>整合測試共用同一座容器與同一份種子資料，因此每個測試都必須自備不會撞號的資料，
 * 才不會因為執行順序不同而互相干擾。
 *
 * <p>領域模型的建構也集中在這裡：{@code Post} 與 {@code Comment} 有十來個欄位，
 * 但每個測試真正在意的通常只有其中一兩個。由這裡供應「其餘欄位皆為合理預設」的樣本，
 * 測試就只需要指名它關心的部分；日後模型再加欄位時，要改的也只有這個檔案。
 */
public final class TestData {

    /** 從 0940000000 起跳，避開 03_DML_seed_data.sql 使用的 091/092/093 號段。 */
    private static final AtomicInteger PHONE_SEQUENCE = new AtomicInteger(40_000_000);

    private static final LocalDateTime FIXED_TIME = LocalDateTime.of(2026, 1, 1, 9, 0);

    private TestData() {}

    /** 產生一組符合 {@code ^09\d{8}$} 且不與既有資料衝突的手機號碼。 */
    public static String uniquePhoneNumber() {
        return "09" + PHONE_SEQUENCE.incrementAndGet();
    }

    /** 一則沒有圖片、沒有標籤、沒有人按讚的發文。 */
    public static Post post(long postId, long userId, String content) {
        return post(postId, userId, content, FIXED_TIME);
    }

    /** 同上，但可指定建立時間——游標分頁的測試需要控制先後順序。 */
    public static Post post(long postId, long userId, String content, LocalDateTime createdAt) {
        return new Post(
                postId,
                userId,
                content,
                null,
                0,
                0,
                false,
                createdAt,
                createdAt,
                "王小明",
                null,
                false,
                List.of());
    }

    /** 一則未經編輯的留言。 */
    public static Comment comment(long commentId, long postId, long userId, String content) {
        return new Comment(commentId, postId, userId, content, FIXED_TIME, FIXED_TIME, "王小明", null, false);
    }

    /** 一位有效（未刪除）的使用者。 */
    public static User user(long userId, String phoneNumber, String userName) {
        return new User(userId, phoneNumber, userName, null, null, null, FIXED_TIME, FIXED_TIME, null);
    }
}
