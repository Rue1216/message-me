package com.esun.social.common.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 圖片上傳設定，前綴 {@code app.upload}。
 *
 * @param directory        實際存放檔案的目錄；容器中掛載為 Docker volume（{@code APP_UPLOAD_DIR}）
 * @param publicBasePath   對外提供圖片的路徑前綴，會寫進資料庫的相對路徑中
 * @param maxFileSizeBytes 單一檔案大小上限
 */
@Validated
@ConfigurationProperties(prefix = "app.upload")
public record UploadProperties(
        @NotBlank String directory, @NotBlank String publicBasePath, @Positive long maxFileSizeBytes) {}
