package com.esun.social.common.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * JWT 設定，前綴 {@code app.jwt}。
 *
 * <p>{@code secret} 只從環境變數 {@code APP_JWT_SECRET} 取得，application.yml 中沒有預設值。
 * 加上 {@link Validated} 之後，忘了設定就會在啟動時直接失敗並指出缺哪一項——
 * 這比帶著一組寫死在版控裡的密鑰跑起來要好得多。
 *
 * @param secret            HMAC-SHA256 簽章密鑰，至少 32 bytes
 * @param issuer            簽發者，驗證時一併比對
 * @param expirationMinutes 權杖有效分鐘數
 */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(@NotBlank String secret, @NotBlank String issuer, @Positive int expirationMinutes) {}
