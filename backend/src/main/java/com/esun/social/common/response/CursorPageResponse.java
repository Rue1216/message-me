package com.esun.social.common.response;

import java.util.List;
import java.util.function.Function;

/**
 * 游標（keyset）分頁結果。
 *
 * <p>時間軸類的列表改用游標而非頁碼：動態牆會持續有新內容插到最前面，
 * offset 分頁在這種資料上必然出錯——讀第 1 頁的同時有人發了文，第 2 頁的起點就整體位移一筆，
 * 同一則發文於是出現兩次。游標以「上一頁最後一筆的位置」為界，不受插入影響。
 *
 * <p>沒有 {@code totalElements} 與 {@code totalPages} 是刻意的：無限捲動不需要總頁數，
 * 而在大資料表上為了顯示一個數字去做 {@code COUNT(*)} 是最不划算的查詢。
 * 是否還有下一頁改由 {@code hasMore} 表示——資料層多取一筆即可判斷，不需額外查詢。
 *
 * @param items      本頁內容
 * @param nextCursor 下一頁的起點；{@code hasMore} 為 {@code false} 時必為 {@code null}。
 *                   對前端不透明，原樣回傳即可（編碼方式見 {@code Cursor}）
 * @param hasMore    是否還有更多資料
 * @param <T>        內容型別
 */
public record CursorPageResponse<T>(List<T> items, String nextCursor, boolean hasMore) {

    public CursorPageResponse {
        items = List.copyOf(items);
    }

    public static <T> CursorPageResponse<T> of(List<T> items, String nextCursor, boolean hasMore) {
        return new CursorPageResponse<>(items, hasMore ? nextCursor : null, hasMore);
    }

    /** 沒有更多資料的一頁。 */
    public static <T> CursorPageResponse<T> last(List<T> items) {
        return new CursorPageResponse<>(items, null, false);
    }

    /**
     * 套用轉換函式到每一筆內容，分頁資訊原封不動。
     *
     * <p>用途與 {@link PageResponse#map} 相同：業務層回傳領域模型，展示層只需
     * {@code .map(XxxResponse::from)} 即可換成回應 DTO。
     */
    public <R> CursorPageResponse<R> map(Function<? super T, ? extends R> mapper) {
        return new CursorPageResponse<>(items.stream().<R>map(mapper).toList(), nextCursor, hasMore);
    }
}
