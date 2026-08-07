package com.esun.social.common.response;

import com.esun.social.common.exception.ErrorCode;

/**
 * 全站統一的 API 回應外殼：{@code { success, data, error }}。
 *
 * <p>{@code data} 與 {@code error} 互斥，未使用的一方為 {@code null}；
 * 搭配 application.yml 的 {@code spring.jackson.default-property-inclusion: non_null}，
 * 序列化時該欄位會整個消失，前端因此可以直接以 {@code success} 分流。
 *
 * @param <T> 成功時的資料型別
 */
public record ApiResponse<T>(boolean success, T data, ApiError error) {

    /** 有回傳內容的成功回應。 */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    /**
     * 沒有回傳內容的成功回應（例如刪除）。
     *
     * <p>命名為 {@code noContent} 而非 {@code success()}，是因為後者會與 record
     * 自動產生的 {@code success()} 存取子撞名。
     */
    public static ApiResponse<Void> noContent() {
        return new ApiResponse<>(true, null, null);
    }

    /**
     * 失敗回應。
     *
     * @param message 對外訊息；傳 {@code null} 時採用 {@code errorCode} 的預設訊息
     */
    public static <T> ApiResponse<T> failure(ErrorCode errorCode, String message) {
        String text = (message == null || message.isBlank()) ? errorCode.defaultMessage() : message;
        return new ApiResponse<>(false, null, new ApiError(errorCode.name(), text));
    }
}
