package com.vlp.stats.repo;

import com.vlp.stats.entity.VisitLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface VisitLogRepository extends JpaRepository<VisitLog, Long> {
    long countByCreatedAtAfter(LocalDateTime after);

    @Query("SELECT COUNT(DISTINCT v.visitorKey) FROM VisitLog v WHERE v.createdAt >= :after")
    long countDistinctVisitorsSince(@Param("after") LocalDateTime after);

    @Query(value = """
            SELECT DATE(created_at) AS d, COUNT(*) AS pv, COUNT(DISTINCT visitor_key) AS uv
            FROM visit_log
            WHERE created_at >= :after
            GROUP BY DATE(created_at)
            ORDER BY d ASC
            """, nativeQuery = true)
    List<Object[]> dailyStats(@Param("after") LocalDateTime after);
}
