package com.esun.social.business.service;

import com.esun.social.business.model.Activity;
import com.esun.social.business.model.User;
import com.esun.social.business.model.UserCredentials;
import com.esun.social.common.exception.BusinessException;
import com.esun.social.common.exception.ErrorCode;
import com.esun.social.common.response.PageResponse;
import com.esun.social.common.util.HtmlSanitizer;
import com.esun.social.common.util.ImagePaths;
import com.esun.social.common.util.PasswordHasher;
import com.esun.social.data.repository.UserRepository;
import java.util.List;
import org.springframework.stereotype.Service;

/** 使用者個人檔案的讀取與維護，以及帳號本身的生命週期。 */
@Service
public class UserService {

    /**
     * 帳號刪除後對外顯示的名稱。
     *
     * <p>文案屬於應用層的決定，因此由這裡傳給 {@code sp_user_soft_delete}，
     * 而不是寫死在 SQL 裡——改字不該需要重建資料庫。
     */
    public static final String DELETED_USER_NAME = "已刪除的使用者";

    private final UserRepository userRepository;
    private final HtmlSanitizer htmlSanitizer;
    private final PasswordHasher passwordHasher;

    public UserService(UserRepository userRepository, HtmlSanitizer htmlSanitizer, PasswordHasher passwordHasher) {
        this.userRepository = userRepository;
        this.htmlSanitizer = htmlSanitizer;
        this.passwordHasher = passwordHasher;
    }

    /**
     * @throws BusinessException 找不到該使用者
     */
    public User findById(long userId) {
        return userRepository
                .findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "找不到這位使用者"));
    }

    /**
     * 更新個人檔案（全欄位取代）。
     *
     * <p>{@code userId} 由呼叫端從權杖取得，不接受請求主體指定——否則任何人都能改別人的檔案。
     * 這也是這個端點不需要額外權限判斷的原因：使用者能操作的對象只有自己。
     *
     * @throws BusinessException 名稱清洗後為空、封面圖不是本站上傳路徑，或使用者不存在
     */
    public User updateProfile(long userId, String userName, String email, String biography, String coverImage) {
        String safeUserName = htmlSanitizer.sanitize(userName);
        if (safeUserName == null || safeUserName.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "使用者名稱不可為空");
        }

        boolean updated = userRepository.updateProfile(
                userId,
                safeUserName,
                normalise(email),
                normalise(biography),
                ImagePaths.requireUploadedOrNull(coverImage));
        if (!updated) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "找不到這位使用者");
        }
        return findById(userId);
    }

    /**
     * 修改密碼。
     *
     * <p>必須提供舊密碼：權杖可能被竊，若只憑權杖就能改密碼，攻擊者能把帳號整個接管過去。
     * 要求舊密碼讓「持有權杖」與「知道密碼」成為兩道獨立的關卡。
     *
     * <p>新鹽一併重新產生（見 {@code sp_user_change_password} 的註解）。
     *
     * @throws BusinessException 舊密碼不正確（401），或使用者不存在（404）
     */
    public void changePassword(long userId, String currentPassword, String newPassword) {
        UserCredentials credentials = userRepository
                .findCredentialsById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "找不到這位使用者"));

        if (!passwordHasher.matches(currentPassword, credentials.passwordSalt(), credentials.passwordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "目前的密碼不正確");
        }

        String salt = passwordHasher.generateSalt();
        if (!userRepository.changePassword(userId, passwordHasher.hash(newPassword, salt), salt)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "找不到這位使用者");
        }
    }

    /**
     * 刪除帳號 —— 軟刪除並匿名化。
     *
     * <p>同樣要求密碼：這是不可逆的操作，只憑一個可能外洩的權杖就執行代價太高。
     *
     * <p>發文與留言會保留，作者顯示為 {@value #DELETED_USER_NAME}。硬刪除會在別人的
     * 討論串裡挖出缺口，且無法復原；軟刪除則同時滿足「使用者的個資消失」與
     * 「他人的對話保持完整」兩件事。
     *
     * @throws BusinessException 密碼不正確（401），或使用者不存在 / 已刪除（404）
     */
    public void deleteAccount(long userId, String currentPassword) {
        UserCredentials credentials = userRepository
                .findCredentialsById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "找不到這位使用者"));

        if (!passwordHasher.matches(currentPassword, credentials.passwordSalt(), credentials.passwordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "密碼不正確");
        }

        if (!userRepository.softDelete(userId, DELETED_USER_NAME)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "找不到這位使用者");
        }
    }

    /**
     * 個人頁的合併動態（發文與留言交錯，新到舊）。
     *
     * <p>採 offset 分頁而非時間軸所用的游標分頁，理由見 {@code sp_user_activity_list} 的註解：
     * 跨兩張資料表的複合游標脆弱且難以驗證，而個人頁的資料量受單一使用者的產出所限。
     *
     * @throws BusinessException 找不到該使用者
     */
    public PageResponse<Activity> listActivities(long userId, int page, int size) {
        // 先確認使用者存在，才不會讓「不存在的 ID」安靜地回傳一個空頁面
        findById(userId);

        long total = userRepository.countActivities(userId);
        int offset = (page - 1) * size;
        List<Activity> activities =
                offset >= total ? List.of() : userRepository.findActivityPage(userId, size, offset);
        return PageResponse.of(activities, page, size, total);
    }

    private String normalise(String value) {
        String sanitised = htmlSanitizer.sanitize(value);
        return sanitised == null || sanitised.isBlank() ? null : sanitised;
    }
}
