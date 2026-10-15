package com.vlp.notify.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notif_user", columnList = "userId")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Notification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** null = broadcast announcement */
    private Long userId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 2000)
    private String content;

    /** SYSTEM / ANNOUNCEMENT / ALERT / CERT */
    @Column(nullable = false, length = 32)
    @Builder.Default
    private String type = "SYSTEM";

    @Builder.Default
    private Boolean readFlag = false;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
