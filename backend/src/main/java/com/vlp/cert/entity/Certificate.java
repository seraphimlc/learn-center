package com.vlp.cert.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "certificates", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cert_user_course", columnNames = {"userId", "courseId"})
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Certificate {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long courseId;

    @Column(nullable = false, length = 64, unique = true)
    private String certNo;

    @Column(length = 200)
    private String courseTitle;

    @Column(length = 100)
    private String userName;

    @Builder.Default
    private Double completionRate = 1.0;

    @CreationTimestamp
    private LocalDateTime issuedAt;
}
