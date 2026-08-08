package com.esun.social.business.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.esun.social.business.model.AuthToken;
import com.esun.social.business.model.User;
import com.esun.social.business.model.UserCredentials;
import com.esun.social.common.exception.BusinessException;
import com.esun.social.common.exception.ErrorCode;
import com.esun.social.common.security.JwtTokenProvider;
import com.esun.social.common.util.HtmlSanitizer;
import com.esun.social.common.util.PasswordHasher;
import com.esun.social.data.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final User EXISTING_USER = new User(
            7L,
            "0912345678",
            "王小明",
            "xiaoming@example.com",
            null,
            "自我介紹",
            LocalDateTime.of(2026, 1, 1, 9, 0),
            LocalDateTime.of(2026, 1, 1, 9, 0),
            null);

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordHasher passwordHasher;

    @Mock
    private JwtTokenProvider tokenProvider;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        // HtmlSanitizer 沒有外部相依且行為確定，用真的比用假的更能反映實際結果
        authService = new AuthService(userRepository, passwordHasher, new HtmlSanitizer(), tokenProvider);
    }

    @Nested
    @DisplayName("註冊")
    class Register {

        @Test
        @DisplayName("以新鹽計算雜湊後寫入，明文密碼不會離開這一層")
        void hashesPasswordWithFreshSalt() {
            when(passwordHasher.generateSalt()).thenReturn("salt");
            when(passwordHasher.hash("Test1234!", "salt")).thenReturn("hash");
            when(userRepository.register("0900000001", "小明", "a@example.com", "hash", "salt"))
                    .thenReturn(7L);
            when(userRepository.findById(7L)).thenReturn(Optional.of(EXISTING_USER));

            User created = authService.register("0900000001", "小明", "Test1234!", "a@example.com");

            assertThat(created).isEqualTo(EXISTING_USER);
            ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);
            verify(userRepository).register(anyString(), anyString(), anyString(), stored.capture(), eq("salt"));
            assertThat(stored.getValue()).isEqualTo("hash").isNotEqualTo("Test1234!");
        }

        @Test
        @DisplayName("使用者名稱中的 HTML 在寫入資料庫前就被清掉")
        void sanitisesUserNameBeforeStoring() {
            when(passwordHasher.generateSalt()).thenReturn("salt");
            when(passwordHasher.hash(anyString(), anyString())).thenReturn("hash");
            when(userRepository.register(anyString(), anyString(), any(), anyString(), anyString()))
                    .thenReturn(7L);
            when(userRepository.findById(7L)).thenReturn(Optional.of(EXISTING_USER));

            authService.register("0900000001", "<script>alert(1)</script>小明", "Test1234!", null);

            verify(userRepository).register(eq("0900000001"), eq("小明"), eq(null), anyString(), anyString());
        }

        @Test
        @DisplayName("使用者名稱清洗後只剩空字串則拒絕，不會寫進資料庫")
        void rejectsUserNameThatIsOnlyMarkup() {
            assertThatThrownBy(() -> authService.register("0900000001", "<b></b>", "Test1234!", null))
                    .isInstanceOf(BusinessException.class)
                    .asInstanceOf(InstanceOfAssertFactories.type(BusinessException.class))
                    .extracting(BusinessException::errorCode)
                    .isEqualTo(ErrorCode.VALIDATION_ERROR);

            verify(userRepository, never()).register(anyString(), anyString(), any(), anyString(), anyString());
        }

        @Test
        @DisplayName("空白的 email 視為未填")
        void treatsBlankEmailAsAbsent() {
            when(passwordHasher.generateSalt()).thenReturn("salt");
            when(passwordHasher.hash(anyString(), anyString())).thenReturn("hash");
            when(userRepository.register(anyString(), anyString(), any(), anyString(), anyString()))
                    .thenReturn(7L);
            when(userRepository.findById(7L)).thenReturn(Optional.of(EXISTING_USER));

            authService.register("0900000001", "小明", "Test1234!", "   ");

            verify(userRepository).register(anyString(), anyString(), eq(null), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("登入")
    class Login {

        @Test
        @DisplayName("密碼正確時簽發權杖")
        void issuesTokenOnValidCredentials() {
            when(userRepository.findByPhoneNumber("0912345678"))
                    .thenReturn(Optional.of(new UserCredentials(EXISTING_USER, "hash", "salt")));
            when(passwordHasher.matches("Test1234!", "salt", "hash")).thenReturn(true);
            when(tokenProvider.createToken(7L, "0912345678")).thenReturn("a.jwt.token");
            when(tokenProvider.expiresInSeconds()).thenReturn(7200L);

            AuthToken token = authService.login("0912345678", "Test1234!");

            assertThat(token.accessToken()).isEqualTo("a.jwt.token");
            assertThat(token.expiresInSeconds()).isEqualTo(7200L);
            assertThat(token.user()).isEqualTo(EXISTING_USER);
        }

        @Test
        @DisplayName("密碼錯誤時不簽發權杖")
        void rejectsWrongPassword() {
            when(userRepository.findByPhoneNumber("0912345678"))
                    .thenReturn(Optional.of(new UserCredentials(EXISTING_USER, "hash", "salt")));
            when(passwordHasher.matches(anyString(), anyString(), anyString())).thenReturn(false);

            assertThatThrownBy(() -> authService.login("0912345678", "wrong"))
                    .isInstanceOf(BusinessException.class)
                    .asInstanceOf(InstanceOfAssertFactories.type(BusinessException.class))
                    .extracting(BusinessException::errorCode)
                    .isEqualTo(ErrorCode.INVALID_CREDENTIALS);

            verify(tokenProvider, never()).createToken(anyLong(), anyString());
        }

        @Test
        @DisplayName("帳號不存在與密碼錯誤回報同一個錯誤，且同樣付出一次雜湊成本")
        void doesNotRevealWhetherAccountExists() {
            when(userRepository.findByPhoneNumber("0900000000")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login("0900000000", "Test1234!"))
                    .isInstanceOf(BusinessException.class)
                    .asInstanceOf(InstanceOfAssertFactories.type(BusinessException.class))
                    .extracting(BusinessException::errorCode)
                    .isEqualTo(ErrorCode.INVALID_CREDENTIALS);

            // 沒有這次運算的話，回應時間會洩漏「這個號碼沒註冊過」
            verify(passwordHasher).matches(eq("Test1234!"), anyString(), anyString());
        }
    }
}
