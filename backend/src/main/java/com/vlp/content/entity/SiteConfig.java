package com.vlp.content.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "site_configs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SiteConfig {
    @Id
    @Column(length = 64)
    private String configKey;

    @Column(length = 4000)
    private String configValue;

    @Column(length = 200)
    private String remark;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
