package com.esun.social.common.security;

import com.esun.social.common.exception.ErrorCode;
import com.esun.social.common.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/**
 * 把安全過濾鏈上的錯誤寫成與其他 API 相同的 {@code ApiResponse} 格式。
 *
 * <p>過濾鏈在 DispatcherServlet 之前就結束了請求，因此 {@code @RestControllerAdvice}
 * 完全碰不到 401／403。少了這個轉換，前端會收到 Spring Security 的預設空白回應，
 * 得為驗證錯誤另寫一套解析邏輯。
 */
@Component
public class SecurityErrorWriter {

    private final ObjectMapper objectMapper;

    public SecurityErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.httpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.failure(errorCode, null));
    }
}
