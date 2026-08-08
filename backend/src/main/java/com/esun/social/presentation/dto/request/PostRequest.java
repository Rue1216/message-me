package com.esun.social.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 新增與編輯發文共用的請求。
 *
 * <p>編輯採全欄位取代語意（HTTP PUT）：{@code image} 不給就是把圖片移除，
 * {@code tags} 不給就是把標籤全部拿掉，而不是「維持原樣」。
 *
 * <p>{@code tags} 在這一層刻意不做任何檢查，數量、長度、字元集與正規化整組交給
 * {@code TagNormalizer}。曾經在這裡放過一條 {@code @Size(max = 10)}，但它會搶在
 * {@code TagNormalizer} 之前觸發，而 Bean Validation 的訊息會被 {@code GlobalExceptionHandler}
 * 冠上欄位名（實際收到的是 {@code tags：標籤最多 10 個}），同一條規則於是有了兩種說法，
 * 也與前端逐字對齊的那句對不上。何況 {@code List<@Pattern...String>} 的錯誤訊息
 * 指不出是第幾個標籤，去重又必須發生在轉小寫之後，Bean Validation 表達不了這個順序。
 *
 * @param content 發文內容，會先經 HTML 清洗再入庫
 * @param image   圖片路徑，須為本站上傳端點回傳的位址
 * @param tags    標籤名稱（不含 {@code #}），未提供時視為沒有標籤
 */
public record PostRequest(
        @NotBlank(message = "請填寫發文內容") @Size(max = 5000, message = "發文內容不可超過 5000 字") String content,
        @Size(max = 500, message = "圖片路徑過長") String image,
        List<String> tags) {

    // 缺欄位與空陣列在語意上是同一件事；在這裡收斂成空清單，
    // 下游就不必每一處都再判斷一次 null
    public PostRequest {
        tags = tags == null ? List.of() : tags;
    }
}
