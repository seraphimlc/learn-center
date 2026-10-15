package com.vlp.ai.repo;

import com.vlp.ai.entity.AiCache;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiCacheRepository extends JpaRepository<AiCache, Long> {
    Optional<AiCache> findByCacheTypeAndRefKey(String cacheType, String refKey);
}
