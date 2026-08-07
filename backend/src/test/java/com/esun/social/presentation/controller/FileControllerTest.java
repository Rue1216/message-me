package com.esun.social.presentation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.esun.social.business.service.ImageStorageService;
import com.esun.social.common.config.SecurityConfig;
import com.esun.social.common.exception.BusinessException;
import com.esun.social.common.exception.ErrorCode;
import com.esun.social.common.exception.GlobalExceptionHandler;
import com.esun.social.common.security.AuthenticatedUser;
import com.esun.social.common.security.SecurityErrorWriter;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(FileController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class, SecurityErrorWriter.class})
class FileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ImageStorageService imageStorageService;

    private static RequestPostProcessor loggedIn() {
        return authentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(7L, "0912345678"), null, List.of()));
    }

    private static MockMultipartFile jpeg() {
        byte[] content = new byte[32];
        content[0] = (byte) 0xFF;
        content[1] = (byte) 0xD8;
        content[2] = (byte) 0xFF;
        return new MockMultipartFile("file", "photo.jpg", "image/jpeg", content);
    }

    @Test
    @DisplayName("上傳成功回 201 與圖片路徑")
    void uploadsImage() throws Exception {
        when(imageStorageService.store(any())).thenReturn("/uploads/abc.jpg");

        mockMvc.perform(multipart("/api/files/images").file(jpeg()).with(loggedIn()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.url").value("/uploads/abc.jpg"));
    }

    @Test
    @DisplayName("未登入不能上傳")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(multipart("/api/files/images").file(jpeg()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        verify(imageStorageService, never()).store(any());
    }

    @Test
    @DisplayName("不支援的格式回 415")
    void reportsUnsupportedMediaType() throws Exception {
        when(imageStorageService.store(any()))
                .thenThrow(new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "只接受 JPEG、PNG 或 WebP 格式的圖片"));

        mockMvc.perform(multipart("/api/files/images").file(jpeg()).with(loggedIn()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    @DisplayName("缺少 file 欄位回 400")
    void reportsMissingFilePart() throws Exception {
        mockMvc.perform(multipart("/api/files/images")
                        .file(new MockMultipartFile("wrongName", "photo.jpg", "image/jpeg", new byte[] {1, 2, 3}))
                        .with(loggedIn()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}
