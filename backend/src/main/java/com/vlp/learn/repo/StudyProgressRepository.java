package com.vlp.learn.repo;

import com.vlp.learn.entity.StudyProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface StudyProgressRepository extends JpaRepository<StudyProgress, Long> {
    Optional<StudyProgress> findByUserIdAndLessonId(Long userId, Long lessonId);
    List<StudyProgress> findByUserIdAndCourseId(Long userId, Long courseId);
    List<StudyProgress> findByUserIdOrderByUpdatedAtDesc(Long userId);

    @Query("SELECT COALESCE(SUM(p.studiedSeconds),0) FROM StudyProgress p")
    Long sumStudiedSeconds();

    @Query("SELECT COUNT(DISTINCT p.userId) FROM StudyProgress p")
    long countDistinctLearners();

    @Query("SELECT p FROM StudyProgress p WHERE p.updatedAt < :before")
    List<StudyProgress> findInactiveSince(@Param("before") LocalDateTime before);

    long countByCompletedTrue();
}
