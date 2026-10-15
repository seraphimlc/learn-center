package com.vlp.content.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "news")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class News {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 512)
    private String coverUrl;

    @Column(length = 500)
    private String summary;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String content;

    /** DRAFT / PUBLISHED */
    @Column(nullable = false, length = 32)
    @Builder.Default
    private String status = "DRAFT";

    private Integer viewCount;

    private LocalDateTime publishedAt;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    private Long createdBy;
}
