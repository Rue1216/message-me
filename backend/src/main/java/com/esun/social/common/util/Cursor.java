package com.esun.social.common.util;

import com.esun.social.common.exception.BusinessException;
import com.esun.social.common.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Base64;

/**
 * 時間軸游標的編解碼。
 *
 * <p>游標是 {@code (createdAt, id)} 這組複合鍵。單用時間不夠——同一秒內的多筆資料會在
 * 分頁邊界上互相遮蔽；補上主鍵作為決勝欄位後，排序才是全序，游標也才唯一。
 *
 * <p><strong>為什麼要編碼</strong><br>
 * 對外只是一個不透明的字串。這讓「游標由哪些欄位組成」成為伺服器可以自由更動的實作細節，
 * 而不是被前端寫死的契約。Base64 不是加密，也沒有打算當成安全機制——
 * 游標的內容本來就是使用者已經看得到的資料，它的作用是防止前端依賴其結構。
 *
 * <p>解碼失敗一律視為輸入錯誤（400）而非伺服器錯誤：游標來自請求參數，
 * 使用者手動改動或貼上過期連結都是可預期的情形。
 */
public final class Cursor {

    private static final String FIELD_SEPARATOR = "|";

    private Cursor() {}

    /** 游標所指向的位置。 */
    public record Position(LocalDateTime createdAt, long id) {}

    /** 將位置編為不透明字串。 */
    public static String encode(LocalDateTime createdAt, long id) {
        String raw = createdAt + FIELD_SEPARATOR + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 解碼游標。
     *
     * @param cursor 來自請求參數，可為 {@code null} 或空字串（代表第一頁）
     * @return 解出的位置；{@code cursor} 為空時回傳 {@code null}
     * @throws BusinessException 游標格式不正確（400）
     */
    public static Position decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int separator = raw.lastIndexOf(FIELD_SEPARATOR);
            if (separator < 0) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "分頁游標格式不正確");
            }
            return new Position(
                    LocalDateTime.parse(raw.substring(0, separator)),
                    Long.parseLong(raw.substring(separator + 1)));
        } catch (IllegalArgumentException | DateTimeParseException e) {
            // 涵蓋 Base64 解碼失敗、時間格式錯誤與數字溢位
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "分頁游標格式不正確");
        }
    }
}
