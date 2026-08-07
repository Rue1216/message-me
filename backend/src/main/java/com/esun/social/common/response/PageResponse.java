package com.esun.social.common.response;

import java.util.List;
import java.util.function.Function;

/**
 * 分頁結果。
 *
 * <p><strong>頁碼自 1 起算</strong>——與前端分頁元件一致，避免在 UI 與 API 之間反覆換算。
 * 資料層需要的 offset 由 {@code (page - 1) * size} 推導。
 *
 * @param items         本頁內容
 * @param page          目前頁碼，最小為 1
 * @param size          每頁筆數，最小為 1
 * @param totalElements 符合條件的總筆數
 * @param totalPages    總頁數；總筆數為 0 時為 0
 * @param <T>           內容型別
 */
public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> of(List<T> items, int page, int size, long totalElements) {
        if (page < 1) {
            throw new IllegalArgumentException("頁碼自 1 起算，收到：" + page);
        }
        if (size < 1) {
            throw new IllegalArgumentException("每頁筆數必須為正整數，收到：" + size);
        }
        if (totalElements < 0) {
            throw new IllegalArgumentException("總筆數不可為負數，收到：" + totalElements);
        }
        // 無條件進位；以整數運算避免 double 在極大筆數下的精度誤差
        int totalPages = (int) ((totalElements + size - 1) / size);
        return new PageResponse<>(List.copyOf(items), page, size, totalElements, totalPages);
    }

    /**
     * 套用轉換函式到每一筆內容，分頁資訊原封不動。
     *
     * <p>讓業務層回傳 {@code PageResponse<領域模型>}，展示層只需 {@code .map(XxxResponse::from)}
     * 就能換成回應 DTO，不必在每個控制器重新拼一次分頁欄位。
     */
    public <R> PageResponse<R> map(Function<? super T, ? extends R> mapper) {
        List<R> mapped = items.stream().<R>map(mapper).toList();
        return new PageResponse<>(mapped, page, size, totalElements, totalPages);
    }
}
