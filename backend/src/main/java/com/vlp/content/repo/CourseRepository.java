package com.vlp.content.repo;

import com.vlp.content.entity.Course;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CourseRepository extends JpaRepository<Course, Long> {
    Page<Course> findByStatus(String status, Pageable pageable);
    List<Course> findTop8ByStatusAndHotTrueOrderBySortOrderAsc(String status);
    List<Course> findTop8ByStatusAndRecommendedTrueOrderBySortOrderAsc(String status);

    @Query("SELECT c FROM Course c WHERE c.status = :status AND (:catId IS NULL OR c.categoryId = :catId) AND (:kw IS NULL OR :kw = '' OR c.title LIKE %:kw% OR c.summary LIKE %:kw%)")
    Page<Course> searchPublished(@Param("status") String status, @Param("catId") Long catId, @Param("kw") String kw, Pageable pageable);

    @Query("SELECT c FROM Course c WHERE (:status IS NULL OR :status = '' OR c.status = :status) AND (:catId IS NULL OR c.categoryId = :catId) AND (:kw IS NULL OR :kw = '' OR c.title LIKE %:kw%)")
    Page<Course> adminSearch(@Param("status") String status, @Param("catId") Long catId, @Param("kw") String kw, Pageable pageable);

    long countByStatus(String status);
}
