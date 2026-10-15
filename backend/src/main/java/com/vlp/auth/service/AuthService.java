package com.vlp.auth.service;

import com.vlp.auth.dto.AuthDtos.*;
import com.vlp.common.BusinessException;
import com.vlp.security.JwtService;
import com.vlp.security.UserPrincipal;
import com.vlp.user.entity.User;
import com.vlp.user.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 账号密码认证。密码使用 BCrypt；微信-only 账号 passwordHash 为空，禁止走密码登录。
 * 公开找回密码默认开启但可关（{@code vlp.auth.public-reset-enabled}），且不区分「用户不存在/邮箱不符」以免枚举。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /** 一期简化找回：无邮件 OTP。生产审计要求关闭时设 false，改由管理员重置。 */
    @Value("${vlp.auth.public-reset-enabled:true}")
    private boolean publicResetEnabled;

    @Transactional
    public AuthResp register(RegisterReq req) {
        assertPasswordPolicy(req.getPassword());
        if (userRepository.existsByUsername(req.getUsername())) {
            throw new BusinessException("用户名已存在");
        }
        if (req.getEmail() != null && !req.getEmail().isBlank() && userRepository.existsByEmail(req.getEmail())) {
            throw new BusinessException("邮箱已被使用");
        }
        User user = User.builder()
                .username(req.getUsername().trim())
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .email(req.getEmail())
                .nickname(req.getNickname() == null || req.getNickname().isBlank() ? req.getUsername() : req.getNickname())
                .role("USER")
                .enabled(true)
                .build();
        userRepository.save(user);
        return toAuth(user);
    }

    @Transactional
    public AuthResp login(LoginReq req) {
        User user = userRepository.findByUsername(req.getUsername())
                .orElseThrow(() -> new BusinessException("用户名或密码错误"));
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw new BusinessException("账号已禁用，请联系管理员");
        }
        if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            throw new BusinessException("该账号仅支持微信登录，请使用微信扫码");
        }
        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            log.warn("auth.login.fail username={}", req.getUsername());
            throw new BusinessException("用户名或密码错误");
        }
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);
        log.info("auth.login.ok userId={} username={}", user.getId(), user.getUsername());
        return toAuth(user);
    }

    @Transactional
    public void resetPassword(ResetPasswordReq req) {
        if (!publicResetEnabled) {
            throw new BusinessException(403, "公开找回密码已关闭，请联系管理员重置");
        }
        assertPasswordPolicy(req.getNewPassword());
        // 统一文案，避免用户名/邮箱枚举
        String mismatch = "账号或邮箱不匹配";
        User user = userRepository.findByUsername(req.getUsername())
                .orElseThrow(() -> new BusinessException(mismatch));
        if (user.getEmail() == null || !user.getEmail().equalsIgnoreCase(req.getEmail())) {
            throw new BusinessException(mismatch);
        }
        if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            throw new BusinessException("该账号为微信登录，请使用微信或联系管理员设置密码");
        }
        user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(user);
    }

    @Transactional
    public void changePassword(UserPrincipal me, ChangePasswordReq req) {
        assertPasswordPolicy(req.getNewPassword());
        User user = userRepository.findById(me.getId()).orElseThrow(() -> new BusinessException("用户不存在"));
        if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            throw new BusinessException("微信账号请先设置密码后再修改");
        }
        if (!passwordEncoder.matches(req.getOldPassword(), user.getPasswordHash())) {
            throw new BusinessException("原密码不正确");
        }
        user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(user);
    }

    @Transactional
    public User updateProfile(UserPrincipal me, UpdateProfileReq req) {
        User user = userRepository.findById(me.getId()).orElseThrow(() -> new BusinessException("用户不存在"));
        if (req.getNickname() != null) user.setNickname(req.getNickname());
        if (req.getEmail() != null) user.setEmail(req.getEmail());
        if (req.getPhone() != null) user.setPhone(req.getPhone());
        if (req.getAvatar() != null) user.setAvatar(req.getAvatar());
        if (req.getBio() != null) user.setBio(req.getBio());
        return userRepository.save(user);
    }

    public User profile(UserPrincipal me) {
        return userRepository.findById(me.getId()).orElseThrow(() -> new BusinessException("用户不存在"));
    }

    public AuthResp toAuth(User user) {
        AuthResp r = new AuthResp();
        r.setToken(jwtService.issue(user.getId(), user.getUsername(), user.getRole()));
        r.setUserId(user.getId());
        r.setUsername(user.getUsername());
        r.setNickname(user.getNickname());
        r.setRole(user.getRole());
        r.setAvatar(user.getAvatar());
        return r;
    }

    /** 最低强度：≥8 且同时含字母与数字（一期可接受；可再加特殊字符策略）。 */
    static void assertPasswordPolicy(String password) {
        if (password == null || password.length() < 8) {
            throw new BusinessException(400, "密码至少 8 位");
        }
        if (password.length() > 64) {
            throw new BusinessException(400, "密码过长");
        }
        boolean hasLetter = false, hasDigit = false;
        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            if (Character.isLetter(c)) hasLetter = true;
            if (Character.isDigit(c)) hasDigit = true;
        }
        if (!hasLetter || !hasDigit) {
            throw new BusinessException(400, "密码需同时包含字母和数字");
        }
    }
}
