package com.vlp.content.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "courses", indexes = {
        @Index(name = "idx_course_category", columnList = "categoryId"),
        @Index(name = "idx_course_status", columnList = "status")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Course {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    private Long categoryId;

    @Column(length = 512)
    private String coverUrl;

    @Column(length = 1000)
    private String summary;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String description;

    /** AI generated outline, pending confirm */
    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String outline;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String aiSummary;

    /** DRAFT / PUBLISHED / OFFLINE */
    @Column(nullable = false, length = 32)
    @Builder.Default
    private String status = "DRAFT";

    @Builder.Default
    private Integer lessonCount = 0;

    /** total seconds */
    @Builder.Default
    private Integer durationSeconds = 0;

    @Builder.Default
    private Integer learnerCount = 0;

    @Builder.Default
    private Boolean recommended = false;

    @Builder.Default
    private Boolean hot = false;

    @Builder.Default
    private Integer sortOrder = 0;

    @Builder.Default
    private Double completionRatio = 0.9;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    private Long createdBy;
}
