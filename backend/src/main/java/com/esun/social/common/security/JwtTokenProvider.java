package com.esun.social.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JWT 的簽發與驗證。
 *
 * <p>採無狀態驗證以符合 RESTful 的要求：伺服器不保存 session，權杖自帶身分與有效期，
 * 水平擴充時不需要共享狀態。代價是<strong>權杖在到期前無法撤銷</strong>，
 * 因此有效期設得短（預設 120 分鐘），且權杖內不放任何會變動的個人資料。
 *
 * <p>{@link #parse} 回傳 {@link Optional} 而非拋出例外：對過濾器來說，
 * 「權杖無效」是每天都在發生的正常情況（過期、被竄改、根本沒帶），
 * 用例外表達會讓正常流程走在例外路徑上。
 */
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    /** HS256 要求密鑰至少 256 bits。 */
    private static final int MINIMUM_SECRET_BYTES = 32;

    private static final String PHONE_CLAIM = "phone";

    private final SecretKey signingKey;
    private final String issuer;
    private final Duration validity;

    public JwtTokenProvider(String secret, String issuer, Duration validity) {
        byte[] keyBytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MINIMUM_SECRET_BYTES) {
            throw new IllegalArgumentException(
                    "JWT 密鑰至少需要 " + MINIMUM_SECRET_BYTES + " bytes，目前為 " + keyBytes.length
                            + " bytes；請於環境變數 APP_JWT_SECRET 設定足夠長度的隨機字串。");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.issuer = issuer;
        this.validity = validity;
    }

    public String createToken(long userId, String phoneNumber) {
        Instant issuedAt = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(Long.toString(userId))
                .claim(PHONE_CLAIM, phoneNumber)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plus(validity)))
                .signWith(signingKey)
                .compact();
    }

    /**
     * @return 權杖有效且簽章、簽發者、有效期都通過時回傳使用者，否則為空
     */
    public Optional<AuthenticatedUser> parse(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(
                    new AuthenticatedUser(Long.parseLong(claims.getSubject()), claims.get(PHONE_CLAIM, String.class)));
        } catch (JwtException | IllegalArgumentException ex) {
            // 只記 debug：無效權杖屬於預期中的日常情況，記成 warn 會讓日誌被掃描流量淹沒
            log.debug("權杖驗證失敗：{}", ex.getMessage());
            return Optional.empty();
        }
    }

    /** 供登入回應告知前端權杖何時到期。 */
    public long expiresInSeconds() {
        return validity.toSeconds();
    }
}
