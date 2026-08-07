package com.esun.social.presentation.controller;

import com.esun.social.business.model.AuthToken;
import com.esun.social.business.model.User;
import com.esun.social.business.service.AuthService;
import com.esun.social.common.response.ApiResponse;
import com.esun.social.presentation.dto.request.LoginRequest;
import com.esun.social.presentation.dto.request.RegisterRequest;
import com.esun.social.presentation.dto.response.CurrentUserResponse;
import com.esun.social.presentation.dto.response.LoginResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 註冊與登入端點，兩者都不需要既有身分。 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** 註冊成功回 201，並帶回新建立的個人檔案（不含權杖，前端仍須走一次登入）。 */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<CurrentUserResponse>> register(@Valid @RequestBody RegisterRequest request) {
        User user = authService.register(
                request.phoneNumber(), request.userName(), request.password(), request.email());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(CurrentUserResponse.from(user)));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthToken token = authService.login(request.phoneNumber(), request.password());
        return ApiResponse.success(LoginResponse.from(token));
    }
}
