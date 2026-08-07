package com.esun.social.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 對外錯誤代碼，同時決定 HTTP 狀態碼與預設訊息。
 *
 * <p>enum 名稱即為回應中的 {@code error.code}，是前端唯一應該據以分支的欄位；
 * {@code error.message} 僅供顯示，可能隨情境調整措辭。
 *
 * <p>命名刻意與 {@code DB/02_DDL_stored_procedures.sql} 的錯誤契約對齊
 * （例如 SP 以 SQLSTATE 45000 拋出的 {@code PHONE_ALREADY_REGISTERED}），
 * 使錯誤能從資料層一路對應到 API 回應而不需轉譯表。
 */
public enum ErrorCode {

    /** 輸入資料未通過 Bean Validation 或型別轉換。 */
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "輸入的資料不符合規則"),

    /** 請求主體無法解析（JSON 格式錯誤、缺少必要片段）。 */
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "請求格式不正確"),

    /** 未提供或提供了無效的存取權杖。 */
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "請先登入"),

    /** 手機號碼或密碼錯誤。刻意不區分兩者，避免成為帳號列舉的管道。 */
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "手機號碼或密碼不正確"),

    /** 已登入，但無權對該資源執行此操作（例如編輯他人的發文）。 */
    FORBIDDEN(HttpStatus.FORBIDDEN, "沒有權限執行這項操作"),

    /** 找不到指定的資源。 */
    NOT_FOUND(HttpStatus.NOT_FOUND, "找不到指定的資料"),

    /** 路徑存在但不支援該 HTTP 方法。 */
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "不支援這個 HTTP 方法"),

    /** 手機號碼已被註冊。 */
    PHONE_ALREADY_REGISTERED(HttpStatus.CONFLICT, "此手機號碼已經註冊過了"),

    /** 上傳內容超過大小上限。 */
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "上傳的內容超過大小上限"),

    /** 內容型別或檔案格式不在白名單內。 */
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "不支援這種檔案或內容格式"),

    /** 未預期的伺服器錯誤。對外一律使用預設訊息，細節只寫入伺服器日誌。 */
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "伺服器發生非預期的錯誤，請稍後再試");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
