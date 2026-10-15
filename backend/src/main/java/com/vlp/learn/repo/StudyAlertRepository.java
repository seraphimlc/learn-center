package com.vlp.learn.repo;

import com.vlp.learn.entity.StudyAlert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StudyAlertRepository extends JpaRepository<StudyAlert, Long> {
    List<StudyAlert> findByUserIdOrderByCreatedAtDesc(Long userId);
}
