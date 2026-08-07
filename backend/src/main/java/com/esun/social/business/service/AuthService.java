package com.esun.social.business.service;

import com.esun.social.business.model.AuthToken;
import com.esun.social.business.model.User;
import com.esun.social.business.model.UserCredentials;
import com.esun.social.common.exception.BusinessException;
import com.esun.social.common.exception.ErrorCode;
import com.esun.social.common.security.JwtTokenProvider;
import com.esun.social.common.util.HtmlSanitizer;
import com.esun.social.common.util.PasswordHasher;
import com.esun.social.data.repository.UserRepository;
import java.util.Base64;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** 註冊與登入。 */
@Service
public class AuthService {

    /**
     * 查無使用者時仍要跑一次雜湊運算所用的假鹽。
     *
     * <p>若在找不到帳號時立刻回傳，登入端點的回應時間會明顯短於「帳號存在但密碼錯誤」，
     * 攻擊者可據此列舉出哪些手機號碼有註冊。多算這一次讓兩條路徑的成本相當。
     */
    private static final String DUMMY_SALT = Base64.getEncoder().encodeToString(new byte[32]);

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final HtmlSanitizer htmlSanitizer;
    private final JwtTokenProvider tokenProvider;

    public AuthService(
            UserRepository userRepository,
            PasswordHasher passwordHasher,
            HtmlSanitizer htmlSanitizer,
            JwtTokenProvider tokenProvider) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.htmlSanitizer = htmlSanitizer;
        this.tokenProvider = tokenProvider;
    }

    /**
     * 註冊。
     *
     * @throws BusinessException 手機號碼已被註冊，或使用者名稱清洗後為空字串
     */
    public User register(String phoneNumber, String userName, String rawPassword, String email) {
        String safeUserName = htmlSanitizer.sanitize(userName);
        if (safeUserName == null || safeUserName.isBlank()) {
            // 例如整串輸入都是 HTML 標籤，清洗後什麼都不剩
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "使用者名稱不可為空");
        }

        String salt = passwordHasher.generateSalt();
        String passwordHash = passwordHasher.hash(rawPassword, salt);

        long userId = userRepository.register(phoneNumber, safeUserName, normalise(email), passwordHash, salt);

        return userRepository
                .findById(userId)
                .orElseThrow(() -> new IllegalStateException("剛註冊的使用者 " + userId + " 查不到"));
    }

    /**
     * 登入。
     *
     * <p>帳號不存在與密碼錯誤都回傳同一個錯誤碼：兩者若能被區分，登入端點就成了
     * 「這個手機號碼有沒有註冊」的查詢介面。
     *
     * @throws BusinessException 手機號碼或密碼不正確
     */
    public AuthToken login(String phoneNumber, String rawPassword) {
        Optional<UserCredentials> found = userRepository.findByPhoneNumber(phoneNumber);

        if (found.isEmpty()) {
            passwordHasher.matches(rawPassword, DUMMY_SALT, DUMMY_SALT);
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        UserCredentials credentials = found.get();
        if (!passwordHasher.matches(rawPassword, credentials.passwordSalt(), credentials.passwordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        User user = credentials.user();
        return new AuthToken(
                tokenProvider.createToken(user.userId(), user.phoneNumber()),
                tokenProvider.expiresInSeconds(),
                user);
    }

    private String normalise(String value) {
        String sanitised = htmlSanitizer.sanitize(value);
        return sanitised == null || sanitised.isBlank() ? null : sanitised;
    }
}
