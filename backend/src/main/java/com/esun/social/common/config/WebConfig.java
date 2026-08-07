package com.esun.social.common.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 層設定：把上傳目錄掛成靜態資源路徑。
 *
 * <p>正式環境由 Nginx 直接讀同一個 volume 提供圖片，請求根本不會進到這裡——
 * 但本地執行與整合測試沒有 Nginx，少了這段就無法驗證「上傳後真的拿得到」。
 * 兩邊對外的路徑一致（{@code /uploads/...}），因此前端不需要區分環境。
 */
@Configuration
@EnableConfigurationProperties(UploadProperties.class)
public class WebConfig implements WebMvcConfigurer {

    private final UploadProperties properties;

    public WebConfig(UploadProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Path.of(properties.directory())
                .toAbsolutePath()
                .normalize()
                .toUri()
                .toString();
        registry.addResourceHandler(properties.publicBasePath() + "/**")
                .addResourceLocations(location)
                .setCachePeriod(3600);
    }
}
