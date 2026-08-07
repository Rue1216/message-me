package com.esun.social.presentation.dto.response;

import java.time.Instant;

/**
 * 服務存活資訊。
 *
 * @param status      固定為 {@code UP}；能回應本端點即代表 MVC 堆疊正常運作
 * @param application 應用程式名稱，供多環境部署時辨識
 * @param timestamp   伺服器當下時間（UTC，ISO-8601）
 */
public record HealthResponse(String status, String application, Instant timestamp) {}
