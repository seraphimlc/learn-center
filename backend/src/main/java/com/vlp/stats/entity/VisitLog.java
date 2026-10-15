package com.vlp.stats.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "visit_log", indexes = {
        @Index(name = "idx_visit_created", columnList = "createdAt"),
        @Index(name = "idx_visit_path", columnList = "path")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class VisitLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 256)
    private String path;

    @Column(length = 64)
    private String visitorKey;

    private Long userId;

    @Column(length = 64)
    private String ip;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
