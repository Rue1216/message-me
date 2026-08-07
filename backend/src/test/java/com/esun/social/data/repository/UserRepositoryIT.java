package com.esun.social.data.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.esun.social.business.model.User;
import com.esun.social.business.model.UserCredentials;
import com.esun.social.common.exception.BusinessException;
import com.esun.social.common.exception.ErrorCode;
import com.esun.social.support.MySqlContainerSupport;
import com.esun.social.support.TestData;
import java.util.Optional;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class UserRepositoryIT extends MySqlContainerSupport {

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("註冊後可依手機號碼取回使用者與其密碼憑證")
    void registersAndFindsByPhoneNumber() {
        String phoneNumber = TestData.uniquePhoneNumber();

        long userId = userRepository.register(phoneNumber, "測試使用者", "test@example.com", "hash", "salt");

        assertThat(userId).isPositive();
        assertThat(userRepository.findByPhoneNumber(phoneNumber))
                .get()
                .satisfies(credentials -> {
                    assertThat(credentials.passwordHash()).isEqualTo("hash");
                    assertThat(credentials.passwordSalt()).isEqualTo("salt");
                    assertThat(credentials.user().userId()).isEqualTo(userId);
                    assertThat(credentials.user().userName()).isEqualTo("測試使用者");
                    assertThat(credentials.user().email()).isEqualTo("test@example.com");
                    assertThat(credentials.user().createdAt()).isNotNull();
                });
    }

    @Test
    @DisplayName("email 可留空")
    void allowsNullEmail() {
        String phoneNumber = TestData.uniquePhoneNumber();

        userRepository.register(phoneNumber, "沒有信箱的人", null, "hash", "salt");

        assertThat(userRepository.findByPhoneNumber(phoneNumber))
                .get()
                .extracting(credentials -> credentials.user().email())
                .isNull();
    }

    @Test
    @DisplayName("重複的手機號碼被擋下，並轉為 PHONE_ALREADY_REGISTERED 業務例外")
    void rejectsDuplicatePhoneNumber() {
        String phoneNumber = TestData.uniquePhoneNumber();
        userRepository.register(phoneNumber, "先註冊的人", null, "hash", "salt");

        assertThatThrownBy(() -> userRepository.register(phoneNumber, "後註冊的人", null, "hash2", "salt2"))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(InstanceOfAssertFactories.type(BusinessException.class))
                .extracting(BusinessException::errorCode)
                .isEqualTo(ErrorCode.PHONE_ALREADY_REGISTERED);
    }

    @Test
    @DisplayName("查無使用者時回傳空的 Optional，而非 null 或例外")
    void returnsEmptyWhenNotFound() {
        assertThat(userRepository.findByPhoneNumber(TestData.uniquePhoneNumber())).isEmpty();
        assertThat(userRepository.findById(999_999L)).isEmpty();
    }

    @Test
    @DisplayName("依 ID 查詢不會回傳密碼欄位，中文亦正確保存")
    void findsByIdWithoutCredentials() {
        // 種子資料的第一位使用者；同時鎖住 DB 腳本的 UTF-8 編碼設定
        Optional<User> user = userRepository.findById(1L);

        assertThat(user).get().satisfies(found -> {
            assertThat(found.userName()).isEqualTo("王小明");
            assertThat(found.phoneNumber()).isEqualTo("0912345678");
            assertThat(found.biography()).contains("咖哩");
        });
    }

    @Test
    @DisplayName("更新個人檔案為全欄位取代，傳入 null 即清空欄位")
    void replacesProfileFields() {
        String phoneNumber = TestData.uniquePhoneNumber();
        long userId = userRepository.register(phoneNumber, "原本的名字", "before@example.com", "hash", "salt");

        boolean updated = userRepository.updateProfile(userId, "改過的名字", null, "新的自我介紹", "/uploads/a.jpg");

        assertThat(updated).isTrue();
        assertThat(userRepository.findById(userId)).get().satisfies(user -> {
            assertThat(user.userName()).isEqualTo("改過的名字");
            assertThat(user.email()).isNull();
            assertThat(user.biography()).isEqualTo("新的自我介紹");
            assertThat(user.coverImage()).isEqualTo("/uploads/a.jpg");
        });
    }

    @Test
    @DisplayName("更新不存在的使用者回報未更新任何資料")
    void reportsNoUpdateForUnknownUser() {
        assertThat(userRepository.updateProfile(999_999L, "誰", null, null, null)).isFalse();
    }

    @Test
    @DisplayName("手機號碼查詢以完整比對，不受 SQL 萬用字元影響")
    void doesNotTreatInputAsPattern() {
        UserCredentials seeded = userRepository.findByPhoneNumber("0912345678").orElseThrow();

        assertThat(seeded.user().userId()).isEqualTo(1L);
        // '%' 在參數繫結下就只是一個普通字元，不會比對到任何人
        assertThat(userRepository.findByPhoneNumber("%")).isEmpty();
        assertThat(userRepository.findByPhoneNumber("' OR '1'='1")).isEmpty();
    }
}
