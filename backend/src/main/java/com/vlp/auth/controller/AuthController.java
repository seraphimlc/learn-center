package com.vlp.auth.controller;

import com.vlp.auth.dto.AuthDtos.*;
import com.vlp.auth.service.AuthService;
import com.vlp.common.ApiResponse;
import com.vlp.security.AuthContext;
import com.vlp.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    public ApiResponse<AuthResp> register(@Valid @RequestBody RegisterReq req) {
        return ApiResponse.ok(authService.register(req));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResp> login(@Valid @RequestBody LoginReq req) {
        return ApiResponse.ok(authService.login(req));
    }

    @PostMapping("/reset-password")
    public ApiResponse<Void> reset(@Valid @RequestBody ResetPasswordReq req) {
        authService.resetPassword(req);
        return ApiResponse.ok("密码已重置", null);
    }

    @GetMapping("/me")
    public ApiResponse<User> me() {
        return ApiResponse.ok(authService.profile(AuthContext.current()));
    }

    @PutMapping("/profile")
    public ApiResponse<User> updateProfile(@RequestBody UpdateProfileReq req) {
        return ApiResponse.ok(authService.updateProfile(AuthContext.current(), req));
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordReq req) {
        authService.changePassword(AuthContext.current(), req);
        return ApiResponse.ok("密码已修改", null);
    }
}
