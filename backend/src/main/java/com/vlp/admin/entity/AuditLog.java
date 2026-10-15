package com.vlp.admin.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_user", columnList = "operatorId"),
        @Index(name = "idx_audit_time", columnList = "createdAt")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long operatorId;

    @Column(length = 64)
    private String operatorName;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(length = 64)
    private String resourceType;

    @Column(length = 64)
    private String resourceId;

    @Column(length = 1000)
    private String detail;

    @Column(length = 64)
    private String ip;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
