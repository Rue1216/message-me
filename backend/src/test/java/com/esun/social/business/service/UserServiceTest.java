package com.esun.social.business.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.esun.social.business.model.User;
import com.esun.social.business.model.UserCredentials;
import com.esun.social.common.exception.BusinessException;
import com.esun.social.common.exception.ErrorCode;
import com.esun.social.common.util.HtmlSanitizer;
import com.esun.social.common.util.PasswordHasher;
import com.esun.social.data.repository.UserRepository;
import com.esun.social.support.TestData;
import java.util.List;
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
class UserServiceTest {

    private static final long USER_ID = 7L;
    private static final String CURRENT_PASSWORD = "Test1234!";

    @Mock
    private UserRepository userRepository;

    private PasswordHasher passwordHasher;
    private UserService userService;
    private UserCredentials credentials;

    @BeforeEach
    void setUp() {
        passwordHasher = new PasswordHasher();
        userService = new UserService(userRepository, new HtmlSanitizer(), passwordHasher);

        User user = TestData.user(USER_ID, "0912345678", "王小明");
        String salt = passwordHasher.generateSalt();
        credentials = new UserCredentials(user, passwordHasher.hash(CURRENT_PASSWORD, salt), salt);
    }

    @Nested
    @DisplayName("修改密碼")
    class ChangingPassword {

        @Test
        @DisplayName("舊密碼不正確時拒絕，且不會寫入任何東西")
        void rejectsWrongCurrentPassword() {
            when(userRepository.findCredentialsById(USER_ID)).thenReturn(Optional.of(credentials));

            assertThatThrownBy(() -> userService.changePassword(USER_ID, "猜錯的密碼", "NewPass123!"))
                    .isInstanceOf(BusinessException.class)
                    .asInstanceOf(InstanceOfAssertFactories.type(BusinessException.class))
                    .extracting(BusinessException::errorCode)
                    .isEqualTo(ErrorCode.INVALID_CREDENTIALS);

            verify(userRepository, never()).changePassword(anyLong(), anyString(), anyString());
        }

        @Test
        @DisplayName("舊密碼正確時寫入新的雜湊，且鹽一併更換")
        void rotatesSaltAlongWithHash() {
            when(userRepository.findCredentialsById(USER_ID)).thenReturn(Optional.of(credentials));
            when(userRepository.changePassword(eq(USER_ID), anyString(), anyString()))
                    .thenReturn(true);

            userService.changePassword(USER_ID, CURRENT_PASSWORD, "NewPass123!");

            ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> salt = ArgumentCaptor.forClass(String.class);
            verify(userRepository).changePassword(eq(USER_ID), hash.capture(), salt.capture());

            assertThat(salt.getValue()).isNotEqualTo(credentials.passwordSalt());
            assertThat(hash.getValue()).isNotEqualTo(credentials.passwordHash());
            // 新雜湊確實對應新密碼與新鹽
            assertThat(passwordHasher.matches("NewPass123!", salt.getValue(), hash.getValue()))
                    .isTrue();
        }

        @Test
        @DisplayName("使用者不存在時回 404")
        void reportsMissingUser() {
            when(userRepository.findCredentialsById(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.changePassword(USER_ID, CURRENT_PASSWORD, "NewPass123!"))
                    .isInstanceOf(BusinessException.class)
                    .asInstanceOf(InstanceOfAssertFactories.type(BusinessException.class))
                    .extracting(BusinessException::errorCode)
                    .isEqualTo(ErrorCode.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("刪除帳號")
    class DeletingAccount {

        @Test
        @DisplayName("密碼不正確時拒絕，且不會刪除任何東西")
        void rejectsWrongPassword() {
            when(userRepository.findCredentialsById(USER_ID)).thenReturn(Optional.of(credentials));

            assertThatThrownBy(() -> userService.deleteAccount(USER_ID, "猜錯的密碼"))
                    .isInstanceOf(BusinessException.class)
                    .asInstanceOf(InstanceOfAssertFactories.type(BusinessException.class))
                    .extracting(BusinessException::errorCode)
                    .isEqualTo(ErrorCode.INVALID_CREDENTIALS);

            verify(userRepository, never()).softDelete(anyLong(), anyString());
        }

        @Test
        @DisplayName("密碼正確時以匿名字串執行軟刪除")
        void softDeletesWithAnonymizedName() {
            when(userRepository.findCredentialsById(USER_ID)).thenReturn(Optional.of(credentials));
            when(userRepository.softDelete(USER_ID, UserService.DELETED_USER_NAME))
                    .thenReturn(true);

            userService.deleteAccount(USER_ID, CURRENT_PASSWORD);

            verify(userRepository).softDelete(USER_ID, UserService.DELETED_USER_NAME);
        }

        @Test
        @DisplayName("重複刪除（SP 回 0 列）時回 404")
        void reportsNotFoundOnRepeatedDeletion() {
            when(userRepository.findCredentialsById(USER_ID)).thenReturn(Optional.of(credentials));
            when(userRepository.softDelete(anyLong(), anyString())).thenReturn(false);

            assertThatThrownBy(() -> userService.deleteAccount(USER_ID, CURRENT_PASSWORD))
                    .isInstanceOf(BusinessException.class)
                    .asInstanceOf(InstanceOfAssertFactories.type(BusinessException.class))
                    .extracting(BusinessException::errorCode)
                    .isEqualTo(ErrorCode.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("合併動態")
    class Activities {

        @Test
        @DisplayName("換算頁碼為 offset")
        void translatesPageNumberToOffset() {
            when(userRepository.findById(USER_ID))
                    .thenReturn(Optional.of(TestData.user(USER_ID, "0912345678", "王小明")));
            when(userRepository.countActivities(USER_ID)).thenReturn(30L);
            when(userRepository.findActivityPage(USER_ID, 10, 10)).thenReturn(List.of());

            assertThat(userService.listActivities(USER_ID, 2, 10).totalPages()).isEqualTo(3);

            verify(userRepository).findActivityPage(USER_ID, 10, 10);
        }

        @Test
        @DisplayName("使用者不存在時回 404，而不是安靜地回空頁面")
        void reportsMissingUser() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.listActivities(USER_ID, 1, 10))
                    .isInstanceOf(BusinessException.class)
                    .asInstanceOf(InstanceOfAssertFactories.type(BusinessException.class))
                    .extracting(BusinessException::errorCode)
                    .isEqualTo(ErrorCode.NOT_FOUND);
        }

        @Test
        @DisplayName("超出總筆數的頁碼回空清單，且不再查詢資料庫")
        void returnsEmptyPageBeyondLastPage() {
            when(userRepository.findById(USER_ID))
                    .thenReturn(Optional.of(TestData.user(USER_ID, "0912345678", "王小明")));
            when(userRepository.countActivities(USER_ID)).thenReturn(5L);

            assertThat(userService.listActivities(USER_ID, 99, 10).items()).isEmpty();

            verify(userRepository, never()).findActivityPage(anyLong(), anyInt(), anyInt());
        }
    }
}
