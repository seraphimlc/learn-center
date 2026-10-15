package com.vlp.content.repo;

import com.vlp.content.entity.News;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NewsRepository extends JpaRepository<News, Long> {
    Page<News> findByStatusOrderByPublishedAtDesc(String status, Pageable pageable);
    Page<News> findByTitleContainingOrderByCreatedAtDesc(String title, Pageable pageable);
}
