package com.esun.social.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JwtTokenProviderTest {

    private static final String SECRET = "test-only-jwt-signing-secret-please-do-not-use-in-production";
    private static final String OTHER_SECRET = "a-completely-different-signing-secret-of-sufficient-length";

    private final JwtTokenProvider provider = new JwtTokenProvider(SECRET, "message-me", Duration.ofMinutes(120));

    @Test
    @DisplayName("簽發的權杖可被解析回原本的使用者")
    void roundTripsTheAuthenticatedUser() {
        String token = provider.createToken(42L, "0912345678");

        assertThat(provider.parse(token))
                .contains(new AuthenticatedUser(42L, "0912345678"));
    }

    @Test
    @DisplayName("回報的有效秒數與設定的存續時間一致")
    void reportsConfiguredLifetime() {
        assertThat(provider.expiresInSeconds()).isEqualTo(Duration.ofMinutes(120).toSeconds());
    }

    @Test
    @DisplayName("過期的權杖不被接受")
    void rejectsExpiredToken() {
        JwtTokenProvider alreadyExpired = new JwtTokenProvider(SECRET, "message-me", Duration.ofSeconds(-60));

        String token = alreadyExpired.createToken(42L, "0912345678");

        assertThat(provider.parse(token)).isEmpty();
    }

    @Test
    @DisplayName("以其他密鑰簽出的權杖不被接受")
    void rejectsTokenSignedWithAnotherKey() {
        String forged = new JwtTokenProvider(OTHER_SECRET, "message-me", Duration.ofMinutes(120))
                .createToken(42L, "0912345678");

        assertThat(provider.parse(forged)).isEmpty();
    }

    @Test
    @DisplayName("竄改過的權杖不被接受")
    void rejectsTamperedToken() {
        String token = provider.createToken(42L, "0912345678");
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("A") ? "B" : "A");

        assertThat(provider.parse(tampered)).isEmpty();
    }

    @Test
    @DisplayName("簽發者不符的權杖不被接受")
    void rejectsTokenFromAnotherIssuer() {
        String token = new JwtTokenProvider(SECRET, "someone-else", Duration.ofMinutes(120))
                .createToken(42L, "0912345678");

        assertThat(provider.parse(token)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "not-a-jwt", "a.b.c"})
    @DisplayName("格式不正確的輸入一律視為無效，不拋例外")
    void rejectsMalformedInput(String token) {
        assertThat(provider.parse(token)).isEmpty();
    }
}
