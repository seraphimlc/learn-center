package com.vlp.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

public class AuthDtos {
    @Data
    public static class RegisterReq {
        @NotBlank(message = "用户名不能为空")
        @Size(min = 3, max = 32, message = "用户名长度 3-32")
        private String username;
        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 64, message = "密码长度至少 6 位")
        private String password;
        private String email;
        private String nickname;
    }

    @Data
    public static class LoginReq {
        @NotBlank(message = "用户名不能为空")
        private String username;
        @NotBlank(message = "密码不能为空")
        private String password;
    }

    @Data
    public static class ResetPasswordReq {
        @NotBlank
        private String username;
        @NotBlank
        private String email;
        @NotBlank
        @Size(min = 6, max = 64)
        private String newPassword;
    }

    @Data
    public static class ChangePasswordReq {
        @NotBlank
        private String oldPassword;
        @NotBlank
        @Size(min = 6, max = 64)
        private String newPassword;
    }

    @Data
    public static class UpdateProfileReq {
        private String nickname;
        private String email;
        private String phone;
        private String avatar;
        private String bio;
    }

    @Data
    public static class AuthResp {
        private String token;
        private Long userId;
        private String username;
        private String nickname;
        private String role;
        private String avatar;
    }
}
