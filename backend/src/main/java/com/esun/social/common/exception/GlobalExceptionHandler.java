package com.esun.social.common.exception;

import com.esun.social.common.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全域例外處理：把所有離開控制器的例外收斂成統一的 {@code ApiResponse} 失敗格式。
 *
 * <p>兩條規則貫穿全部處理方法：
 * <ol>
 *   <li><strong>可預期的錯誤</strong>（驗證失敗、找不到資料、權限不足）回傳具體訊息，
 *       讓使用者知道怎麼修正。</li>
 *   <li><strong>非預期的錯誤</strong>只寫入伺服器日誌，對外一律回制式訊息。
 *       例外訊息可能含 SQL 片段、檔案路徑或設定值，直接回傳等同於資訊洩漏。</li>
 * </ol>
 *
 * <p>application.yml 另以 {@code server.error.include-message: never} 等設定關閉
 * Spring Boot 內建錯誤頁的細節，堵住繞過本處理器的路徑。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 業務規則不成立 —— 屬於正常流程的一部分，只記 debug。 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        log.debug("業務例外 {}：{}", ex.errorCode(), ex.getMessage());
        return build(ex.errorCode(), ex.getMessage());
    }

    /** 請求主體的 Bean Validation 失敗。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleBodyValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getAllErrors().stream()
                .map(error -> error instanceof FieldError fieldError
                        ? fieldError.getField() + "：" + fieldError.getDefaultMessage()
                        : error.getDefaultMessage())
                .collect(Collectors.joining("；"));
        return build(ErrorCode.VALIDATION_ERROR, message);
    }

    /** 查詢字串或路徑參數上的 Bean Validation 失敗（@Validated 標註的控制器）。 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleParameterValidation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + "：" + violation.getMessage())
                .collect(Collectors.joining("；"));
        return build(ErrorCode.VALIDATION_ERROR, message);
    }

    /** 參數缺漏或型別不符。訊息只帶參數名稱，不回傳原始輸入值以免成為反射式輸出。 */
    @ExceptionHandler({
        MethodArgumentTypeMismatchException.class,
        MissingServletRequestParameterException.class,
        MissingServletRequestPartException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleBadParameter(Exception ex) {
        String parameterName = switch (ex) {
            case MethodArgumentTypeMismatchException mismatch -> mismatch.getName();
            case MissingServletRequestParameterException missing -> missing.getParameterName();
            case MissingServletRequestPartException missingPart -> missingPart.getRequestPartName();
            default -> "參數";
        };
        return build(ErrorCode.VALIDATION_ERROR, parameterName + "：參數缺漏或格式不正確");
    }

    /** 請求主體不是合法 JSON。解析器的原始訊息會夾帶輸入片段，不對外揭露。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.debug("請求主體無法解析：{}", ex.getMessage());
        return build(ErrorCode.MALFORMED_REQUEST, null);
    }

    /** 路徑存在但方法不對。依 RFC 9110 附上 Allow 標頭。 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        HttpHeaders headers = new HttpHeaders();
        if (ex.getSupportedHttpMethods() != null) {
            headers.setAllow(ex.getSupportedHttpMethods());
        }
        return ResponseEntity.status(ErrorCode.METHOD_NOT_ALLOWED.httpStatus())
                .headers(headers)
                .body(ApiResponse.failure(ErrorCode.METHOD_NOT_ALLOWED, null));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        return build(ErrorCode.UNSUPPORTED_MEDIA_TYPE, null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        return build(ErrorCode.PAYLOAD_TOO_LARGE, null);
    }

    /** 路徑不存在。統一成 ApiResponse，避免落到容器預設的 HTML 錯誤頁。 */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleNotFound(Exception ex) {
        return build(ErrorCode.NOT_FOUND, null);
    }

    /** 最後一道防線：任何漏網的例外都在此收斂，細節只留在伺服器日誌。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("未預期的例外", ex);
        return build(ErrorCode.INTERNAL_ERROR, null);
    }

    private ResponseEntity<ApiResponse<Void>> build(ErrorCode errorCode, String message) {
        return ResponseEntity.status(errorCode.httpStatus()).body(ApiResponse.failure(errorCode, message));
    }
}
