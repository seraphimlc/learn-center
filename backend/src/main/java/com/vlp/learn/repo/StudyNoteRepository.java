package com.vlp.learn.repo;

import com.vlp.learn.entity.StudyNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StudyNoteRepository extends JpaRepository<StudyNote, Long> {
    List<StudyNote> findByUserIdAndLessonIdOrderByPositionSecondsAsc(Long userId, Long lessonId);
    List<StudyNote> findByUserIdOrderByCreatedAtDesc(Long userId);
}
