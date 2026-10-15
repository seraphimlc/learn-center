package com.vlp.content.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "lessons", indexes = {
        @Index(name = "idx_lesson_course", columnList = "courseId")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Lesson {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long courseId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 1000)
    private String description;

    /** relative path under uploads, e.g. videos/xxx.mp4 */
    @Column(length = 512)
    private String videoPath;

    @Builder.Default
    private Integer durationSeconds = 0;

    @Builder.Default
    private Integer sortOrder = 0;

    @Builder.Default
    private Boolean freePreview = false;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
