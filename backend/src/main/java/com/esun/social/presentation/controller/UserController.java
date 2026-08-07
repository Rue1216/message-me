package com.esun.social.presentation.controller;

import com.esun.social.business.service.UserService;
import com.esun.social.common.response.ApiResponse;
import com.esun.social.common.security.AuthenticatedUser;
import com.esun.social.presentation.dto.request.UpdateProfileRequest;
import com.esun.social.presentation.dto.response.CurrentUserResponse;
import com.esun.social.presentation.dto.response.UserResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 使用者個人檔案端點。 */
@RestController
@RequestMapping("/api/users")
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

    /** 他人的公開檔案，不含手機號碼與電子郵件。 */
    @GetMapping("/{userId}")
    public ApiResponse<UserResponse> publicProfile(@PathVariable long userId) {
        return ApiResponse.success(UserResponse.from(userService.findById(userId)));
    }
}
