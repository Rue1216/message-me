package com.esun.social.presentation.controller;

import com.esun.social.business.service.ImageStorageService;
import com.esun.social.common.response.ApiResponse;
import com.esun.social.presentation.dto.response.UploadedImageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 圖片上傳端點。
 *
 * <p>上傳與使用分成兩步：這裡只回傳一個路徑，發文或個人檔案再引用它。
 * 好處是同一張圖可以先上傳、預覽，使用者反悔也不必連帶處理已建立的發文。
 */
@RestController
@RequestMapping("/api/files")
public class FileController {

    private final ImageStorageService imageStorageService;

    public FileController(ImageStorageService imageStorageService) {
        this.imageStorageService = imageStorageService;
    }

    /** 需要登入。回傳的路徑可直接填入發文的 {@code image} 或個人檔案的 {@code coverImage}。 */
    @PostMapping("/images")
    public ResponseEntity<ApiResponse<UploadedImageResponse>> upload(@RequestPart("file") MultipartFile file) {
        String url = imageStorageService.store(file);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(new UploadedImageResponse(url)));
    }
}
