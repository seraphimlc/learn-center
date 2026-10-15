package com.vlp.auth.service;

import com.vlp.auth.dto.AuthDtos.*;
import com.vlp.common.BusinessException;
import com.vlp.security.JwtService;
import com.vlp.security.UserPrincipal;
import com.vlp.user.entity.User;
import com.vlp.user.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public AuthResp register(RegisterReq req) {
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
            throw new BusinessException("用户名或密码错误");
        }
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);
        return toAuth(user);
    }

    @Transactional
    public void resetPassword(ResetPasswordReq req) {
        User user = userRepository.findByUsername(req.getUsername())
                .orElseThrow(() -> new BusinessException("用户不存在"));
        if (user.getEmail() == null || !user.getEmail().equalsIgnoreCase(req.getEmail())) {
            throw new BusinessException("邮箱与账号不匹配");
        }
        user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(user);
    }

    @Transactional
    public void changePassword(UserPrincipal me, ChangePasswordReq req) {
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
}
