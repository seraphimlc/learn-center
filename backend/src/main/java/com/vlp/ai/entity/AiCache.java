package com.vlp.ai.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_cache", indexes = {
        @Index(name = "uk_ai_cache_type_ref", columnList = "cacheType,refKey", unique = true)
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AiCache {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String cacheType;

    @Column(nullable = false, length = 128)
    private String refKey;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String contentJson;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
