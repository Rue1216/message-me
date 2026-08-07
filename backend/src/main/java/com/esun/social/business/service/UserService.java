package com.esun.social.business.service;

import com.esun.social.business.model.User;
import com.esun.social.common.exception.BusinessException;
import com.esun.social.common.exception.ErrorCode;
import com.esun.social.common.util.HtmlSanitizer;
import com.esun.social.common.util.ImagePaths;
import com.esun.social.data.repository.UserRepository;
import org.springframework.stereotype.Service;

/** 使用者個人檔案的讀取與維護。 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final HtmlSanitizer htmlSanitizer;

    public UserService(UserRepository userRepository, HtmlSanitizer htmlSanitizer) {
        this.userRepository = userRepository;
        this.htmlSanitizer = htmlSanitizer;
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

    private String normalise(String value) {
        String sanitised = htmlSanitizer.sanitize(value);
        return sanitised == null || sanitised.isBlank() ? null : sanitised;
    }
}
