package com.esun.social.common.config;

import com.esun.social.common.exception.ErrorCode;
import com.esun.social.common.security.JwtAuthenticationFilter;
import com.esun.social.common.security.JwtTokenProvider;
import com.esun.social.common.security.SecurityErrorWriter;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 安全設定 —— 無狀態 JWT 驗證。
 *
 * <h2>存取原則</h2>
 * 讀取公開內容不需要登入（動態牆、單篇發文、留言、他人的公開檔案），
 * 任何會改變資料的操作都必須登入。這對應社群平台的實際使用情境：
 * 訪客可以先看看再決定要不要註冊。
 *
 * <h2>關掉 CSRF 的理由</h2>
 * CSRF 攻擊的前提是瀏覽器會自動帶上憑證（Cookie）。本專案的權杖存在前端並由
 * {@code Authorization} 標頭手動附加，瀏覽器不會替第三方網站代送，因此 CSRF token
 * 在此不提供額外保護，只會讓無狀態 API 多一次來回。
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    @Bean
    JwtTokenProvider jwtTokenProvider(JwtProperties properties) {
        return new JwtTokenProvider(
                properties.secret(), properties.issuer(), Duration.ofMinutes(properties.expirationMinutes()));
    }

    @Bean
    JwtAuthenticationFilter jwtAuthenticationFilter(JwtTokenProvider tokenProvider) {
        return new JwtAuthenticationFilter(tokenProvider);
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter, SecurityErrorWriter errorWriter)
            throws Exception {

        return http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 不使用表單登入與 HTTP Basic：憑證一律走 /api/auth/login 換取權杖
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/health", "/actuator/health")
                        .permitAll()
                        // 必須排在 /api/users/* 之前，否則會被萬用字元規則吃掉而變成公開
                        .requestMatchers(HttpMethod.GET, "/api/users/me")
                        .authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/users/*", "/api/users/*/activities")
                        .permitAll()
                        // /api/posts/search 與 /api/posts/{postId} 都符合 /api/posts/*，
                        // 兩者的存取原則相同（公開），因此不需要拆開。
                        .requestMatchers(HttpMethod.GET, "/api/posts", "/api/posts/*", "/api/posts/*/comments")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/tags/popular", "/api/tags/*/posts")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/uploads/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, ex) ->
                                errorWriter.write(response, ErrorCode.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, ex) ->
                                errorWriter.write(response, ErrorCode.FORBIDDEN)))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
