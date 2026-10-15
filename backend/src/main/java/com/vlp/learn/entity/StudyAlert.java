package com.vlp.learn.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "study_alerts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StudyAlert {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    private Long courseId;

    /** INACTIVE / LAGGING */
    @Column(nullable = false, length = 32)
    private String type;

    @Column(length = 500)
    private String message;

    @Builder.Default
    private Boolean notified = false;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
