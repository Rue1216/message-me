package com.esun.social.common.exception;

/**
 * 業務規則不成立時拋出的例外，由 {@link GlobalExceptionHandler} 轉為對外回應。
 *
 * <p>訊息內容會原封不動地送到使用者端，因此僅能放可公開的說明，
 * 不得夾帶 SQL、路徑或其他內部細節。
 */
public class BusinessException extends RuntimeException {

    private final transient ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage());
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message != null ? message : errorCode.defaultMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
        super(message != null ? message : errorCode.defaultMessage(), cause);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
