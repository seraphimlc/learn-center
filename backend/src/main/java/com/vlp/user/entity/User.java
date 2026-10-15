package com.vlp.user.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_user_username", columnList = "username", unique = true),
        @Index(name = "idx_user_email", columnList = "email"),
        @Index(name = "idx_user_wechat_openid", columnList = "wechatOpenId", unique = true),
        @Index(name = "idx_user_wechat_unionid", columnList = "wechatUnionId", unique = true)
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String username;

    /** Nullable for WeChat-only accounts (no password set). */
    @JsonIgnore
    @Column(length = 128)
    private String passwordHash;

    @Column(length = 128)
    private String email;

    @Column(length = 64)
    private String nickname;

    @Column(length = 512)
    private String avatar;

    @Column(length = 32)
    private String phone;

    /** USER / ADMIN */
    @Column(nullable = false, length = 32)
    @Builder.Default
    private String role = "USER";

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(length = 512)
    private String bio;

    /** WeChat Open Platform / Official Account openid (nullable unique). */
    @Column(length = 64)
    private String wechatOpenId;

    /** WeChat unionid when available (nullable unique). */
    @Column(length = 64)
    private String wechatUnionId;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    private LocalDateTime lastLoginAt;
}
