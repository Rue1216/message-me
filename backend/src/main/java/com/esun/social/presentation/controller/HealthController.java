package com.esun.social.presentation.controller;

import com.esun.social.common.response.ApiResponse;
import com.esun.social.presentation.dto.response.HealthResponse;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公開的健康檢查端點。
 *
 * <p>與 {@code /actuator/health} 分工不同：actuator 供 Docker healthcheck 判斷容器狀態，
 * 本端點則是 {@code /api/**} 這條對外路徑的端到端驗證——經過 Nginx 反向代理、
 * Security 過濾鏈與統一回應格式，是審核者確認三層都串起來的最短路徑。
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final String applicationName;

    public HealthController(@Value("${spring.application.name}") String applicationName) {
        this.applicationName = applicationName;
    }

    @GetMapping
    public ApiResponse<HealthResponse> health() {
        return ApiResponse.success(new HealthResponse("UP", applicationName, Instant.now()));
    }
}
