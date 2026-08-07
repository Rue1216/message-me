package com.esun.social.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 新增與編輯發文共用的請求。
 *
 * <p>編輯採全欄位取代語意（HTTP PUT）：{@code image} 不給就是把圖片移除，
 * 而不是「維持原樣」。
 *
 * @param content 發文內容，會先經 HTML 清洗再入庫
 * @param image   圖片路徑，須為本站上傳端點回傳的位址
 */
public record PostRequest(
        @NotBlank(message = "請填寫發文內容") @Size(max = 5000, message = "發文內容不可超過 5000 字") String content,
        @Size(max = 500, message = "圖片路徑過長") String image) {}
