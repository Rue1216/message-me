package com.esun.social.presentation.dto.response;

/**
 * 圖片上傳結果。
 *
 * @param url 圖片的相對路徑；前端把它原樣填進發文或個人檔案的 {@code image} / {@code coverImage} 欄位
 */
public record UploadedImageResponse(String url) {}
