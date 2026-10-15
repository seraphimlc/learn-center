package com.vlp.learn.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "study_progress", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_lesson", columnNames = {"userId", "lessonId"})
}, indexes = {
        @Index(name = "idx_sp_user_course", columnList = "userId,courseId"),
        @Index(name = "idx_sp_user_updated", columnList = "userId,updatedAt")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StudyProgress {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long courseId;

    @Column(nullable = false)
    private Long lessonId;

    /** last position in seconds */
    @Builder.Default
    private Integer positionSeconds = 0;

    /** max reached position (anti-cheat) */
    @Builder.Default
    private Integer maxPositionSeconds = 0;

    @Builder.Default
    private Integer studiedSeconds = 0;

    @Builder.Default
    private Boolean completed = false;

    private LocalDateTime completedAt;

    private LocalDateTime lastHeartbeatAt;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
