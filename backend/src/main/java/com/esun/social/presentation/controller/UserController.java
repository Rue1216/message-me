package com.esun.social.presentation.controller;

import com.esun.social.business.service.UserService;
import com.esun.social.common.response.ApiResponse;
import com.esun.social.common.response.PageResponse;
import com.esun.social.common.security.AuthenticatedUser;
import com.esun.social.presentation.dto.request.ChangePasswordRequest;
import com.esun.social.presentation.dto.request.DeleteAccountRequest;
import com.esun.social.presentation.dto.request.UpdateProfileRequest;
import com.esun.social.presentation.dto.response.ActivityResponse;
import com.esun.social.presentation.dto.response.CurrentUserResponse;
import com.esun.social.presentation.dto.response.UserResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 使用者個人檔案端點。 */
@RestController
@RequestMapping("/api/users")
@Validated
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /** 本人的完整檔案。使用者 ID 取自權杖，不接受由呼叫端指定。 */
    @GetMapping("/me")
    public ApiResponse<CurrentUserResponse> currentUser(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.success(CurrentUserResponse.from(userService.findById(principal.userId())));
    }

    /** 更新本人的個人檔案。全欄位取代，未提供的欄位即為清空。 */
    @PutMapping("/me")
    public ApiResponse<CurrentUserResponse> updateCurrentUser(
            @AuthenticationPrincipal AuthenticatedUser principal, @Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.success(CurrentUserResponse.from(userService.updateProfile(
                principal.userId(),
                request.userName(),
                request.email(),
                request.biography(),
                request.coverImage())));
    }

    /**
     * 修改密碼。需要提供目前的密碼，理由見 {@code ChangePasswordRequest}。
     *
     * <p>成功後刻意<strong>不</strong>簽發新權杖，也不使既有權杖失效：本專案的 JWT 是無狀態的，
     * 沒有撤銷清單，做不到「改密碼後讓其他裝置登出」。與其提供一個看似有效實則無效的保證，
     * 不如誠實地維持現狀——權杖仍會在原本的有效期後自然過期。
     */
    @PutMapping("/me/password")
    public ApiResponse<Void> changePassword(
            @AuthenticationPrincipal AuthenticatedUser principal, @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(principal.userId(), request.currentPassword(), request.newPassword());
        return ApiResponse.noContent();
    }

    /** 刪除帳號（軟刪除並匿名化）。發文與留言會保留，作者顯示為「已刪除的使用者」。 */
    @DeleteMapping("/me")
    public ApiResponse<Void> deleteAccount(
            @AuthenticationPrincipal AuthenticatedUser principal, @Valid @RequestBody DeleteAccountRequest request) {
        userService.deleteAccount(principal.userId(), request.password());
        return ApiResponse.noContent();
    }

    /** 他人的公開檔案，不含手機號碼與電子郵件。 */
    @GetMapping("/{userId}")
    public ApiResponse<UserResponse> publicProfile(@PathVariable long userId) {
        return ApiResponse.success(UserResponse.from(userService.findById(userId)));
    }

    /**
     * 某使用者的發文與留言合併時間軸，新到舊。公開。
     *
     * <p>採頁碼分頁而非時間軸的游標分頁，理由見 {@code sp_user_activity_list}：
     * 跨兩張資料表的複合游標脆弱且難以驗證，而個人頁的資料量受單一使用者的產出所限。
     */
    @GetMapping("/{userId}/activities")
    public ApiResponse<PageResponse<ActivityResponse>> activities(
            @PathVariable long userId,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "頁碼自 1 起算") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "每頁至少 1 筆")
                    @Max(value = 100, message = "每頁最多 100 筆")
                    int size) {
        return ApiResponse.success(
                userService.listActivities(userId, page, size).map(ActivityResponse::from));
    }
}
