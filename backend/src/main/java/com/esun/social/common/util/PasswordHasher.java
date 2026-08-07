package com.esun.social.common.util;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.stereotype.Component;

/**
 * 密碼雜湊 —— PBKDF2-HMAC-SHA256，每位使用者一組獨立的鹽。
 *
 * <p>參數與 {@code DB/README.md}「密碼儲存契約」逐項對應，
 * 改動任一項都會使 {@code 03_DML_seed_data.sql} 的示範帳號無法登入
 * （{@code PasswordHasherTest} 會即時攔下這件事）。
 *
 * <p><strong>為什麼是獨立的 salt 欄位而不是 BCrypt</strong><br>
 * 規格明文要求密碼「加鹽並經雜湊後儲存」。BCrypt 把鹽編在雜湊字串裡，
 * 稽核者得讀程式碼才能確認有加鹽；獨立欄位則在 Schema 上就看得見。
 *
 * <p>疊代次數 310,000 採用 OWASP 對 PBKDF2-HMAC-SHA256 的建議值，
 * 單次運算約數百毫秒——這個成本正是它的用途：讓離線暴力破解變得昂貴。
 */
@Component
public class PasswordHasher {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 310_000;
    private static final int KEY_LENGTH_BITS = 256;
    private static final int SALT_LENGTH_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    /** 產生新的鹽（32 bytes 亂數，Base64 編碼後為 44 字元）。 */
    public String generateSalt() {
        byte[] salt = new byte[SALT_LENGTH_BYTES];
        secureRandom.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    /**
     * 以指定的鹽計算雜湊。
     *
     * @param rawPassword 明文密碼
     * @param saltBase64  {@link #generateSalt()} 產生的鹽
     * @return 導出金鑰的 Base64 編碼
     */
    public String hash(String rawPassword, String saltBase64) {
        byte[] salt = Base64.getDecoder().decode(saltBase64);
        return Base64.getEncoder().encodeToString(deriveKey(rawPassword, salt));
    }

    /**
     * 驗證密碼。
     *
     * <p>以 {@link MessageDigest#isEqual} 做定時比較：一般的字串相等比較會在第一個
     * 不同的位元組就返回，攻擊者可藉由回應時間逐位元組推敲出正確的雜湊。
     *
     * <p>鹽或雜湊無法解碼時回傳 {@code false} 而非拋出例外——資料損毀不該讓登入端點
     * 回 500，對呼叫端而言它就只是「這組憑證無法驗證通過」。
     */
    public boolean matches(String rawPassword, String saltBase64, String expectedHashBase64) {
        if (rawPassword == null || saltBase64 == null || expectedHashBase64 == null) {
            return false;
        }
        try {
            byte[] salt = Base64.getDecoder().decode(saltBase64);
            byte[] expected = Base64.getDecoder().decode(expectedHashBase64);
            return MessageDigest.isEqual(deriveKey(rawPassword, salt), expected);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private byte[] deriveKey(String rawPassword, byte[] salt) {
        try {
            KeySpec spec = new PBEKeySpec(rawPassword.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS);
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (java.security.NoSuchAlgorithmException | java.security.spec.InvalidKeySpecException ex) {
            // PBKDF2WithHmacSHA256 是 JDK 標準演算法，走到這裡代表執行環境本身有問題
            throw new IllegalStateException("無法計算密碼雜湊：" + ALGORITHM + " 不可用", ex);
        }
    }
}
