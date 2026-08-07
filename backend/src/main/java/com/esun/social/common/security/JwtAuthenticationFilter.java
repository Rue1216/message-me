package com.esun.social.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 從 {@code Authorization: Bearer <token>} 標頭還原使用者身分。
 *
 * <p><strong>本過濾器不拒絕任何請求。</strong>沒帶權杖、權杖無效或已過期時，
 * 它只是不設定 SecurityContext，然後放行——該不該擋由授權規則決定。
 * 這讓「未登入也能瀏覽動態牆」與「帶著過期權杖瀏覽公開頁面」都自然成立，
 * 而 401 的產生集中在一處（{@code SecurityConfig} 的 entry point），不會散落在過濾器裡。
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        extractBearerToken(request)
                .flatMap(tokenProvider::parse)
                .ifPresent(user -> SecurityContextHolder.getContext()
                        .setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of())));

        filterChain.doFilter(request, response);
    }

    private Optional<String> extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }
        return Optional.of(header.substring(BEARER_PREFIX.length()).trim());
    }
}
