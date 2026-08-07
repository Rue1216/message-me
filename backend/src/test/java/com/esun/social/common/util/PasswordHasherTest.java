package com.esun.social.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PasswordHasherTest {

    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    @DisplayName("鹽為 32 bytes 的 Base64，且每次產生皆不同")
    void generatesDistinct32ByteSalts() {
        String first = hasher.generateSalt();
        String second = hasher.generateSalt();

        assertThat(Base64.getDecoder().decode(first)).hasSize(32);
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("相同密碼與鹽必得相同雜湊；換了鹽就不同")
    void hashesDeterministicallyPerSalt() {
        String salt = hasher.generateSalt();

        assertThat(hasher.hash("Test1234!", salt)).isEqualTo(hasher.hash("Test1234!", salt));
        assertThat(hasher.hash("Test1234!", salt)).isNotEqualTo(hasher.hash("Test1234!", hasher.generateSalt()));
    }

    @Test
    @DisplayName("密碼正確才通過驗證")
    void verifiesOnlyTheCorrectPassword() {
        String salt = hasher.generateSalt();
        String hash = hasher.hash("Test1234!", salt);

        assertThat(hasher.matches("Test1234!", salt, hash)).isTrue();
        assertThat(hasher.matches("test1234!", salt, hash)).isFalse();
        assertThat(hasher.matches("", salt, hash)).isFalse();
    }

    /**
     * 鎖住與資料庫的契約。
     *
     * <p>這組鹽與雜湊直接取自 {@code DB/03_DML_seed_data.sql} 的示範帳號。只要有人改動了
     * 演算法、疊代次數或編碼方式，這個測試就會紅燈——而不是等到有人發現示範帳號登不進去。
     */
    @Test
    @DisplayName("與 DB/README.md 的密碼儲存契約一致：示範帳號的雜湊可被驗證")
    void matchesSeedDataContract() {
        String seedSalt = "TbKyTKQvxnX97ehfRS2efDLVA3nc6NlH9yWduLHMtGE=";
        String seedHash = "5rj8GO8PnCh8FCkNRyl6Y9s4T/NARLuDhx+/CYI7A0A=";

        assertThat(hasher.matches("Test1234!", seedSalt, seedHash)).isTrue();
    }

    @Test
    @DisplayName("鹽或雜湊不是合法 Base64 時視為驗證失敗，不拋例外")
    void treatsCorruptedCredentialsAsMismatch() {
        assertThat(hasher.matches("Test1234!", "not-base64!!", "also-not-base64!!")).isFalse();
    }
}
