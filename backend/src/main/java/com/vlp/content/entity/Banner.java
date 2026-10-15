package com.vlp.content.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "banners")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Banner {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 512)
    private String imageUrl;

    @Column(length = 512)
    private String linkUrl;

    @Builder.Default
    private Integer sortOrder = 0;

    @Builder.Default
    private Boolean enabled = true;
}
