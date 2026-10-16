package com.vlp.learn.service;

import com.vlp.common.BusinessException;
import com.vlp.content.entity.Course;
import com.vlp.content.entity.Lesson;
import com.vlp.content.repo.CourseRepository;
import com.vlp.content.repo.LessonRepository;
import com.vlp.learn.dto.LearnDtos.*;
import com.vlp.learn.entity.*;
import com.vlp.learn.async.ProgressBufferService;
import com.vlp.learn.repo.*;
import com.vlp.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.Comparator;

/**
 * 学习域核心服务。播放进度（position）仅写 Redis（ProgressBufferService）；
 * 读路径 prefer Redis，miss 视为 0，不依赖 MySQL 做续播位置。
 * 课时完成时稀疏写一次 MySQL 供证书；课程进度%=完成课时/总课时（Redis 完成标记）。
 */
@Service
@RequiredArgsConstructor
public class LearnService {
    private final StudyNoteRepository noteRepository;
    private final BookmarkRepository bookmarkRepository;
    private final LessonRepository lessonRepository;
    private final CourseRepository courseRepository;
    private final ProgressBufferService progressBufferService;

    public StudyProgress reportProgress(UserPrincipal me, ProgressReport req) {
        // Always Redis-only for heartbeats (no MySQL position path).
        return progressBufferService.accept(me, req);
    }

    public List<Map<String, Object>> myLearning(Long userId) {
        List<StudyProgress> all = progressBufferService.listUserProgress(userId);
        Map<Long, StudyProgress> latestByCourse = new LinkedHashMap<>();
        for (StudyProgress p : all) {
            latestByCourse.putIfAbsent(p.getCourseId(), p);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<Long, StudyProgress> e : latestByCourse.entrySet()) {
            Long courseId = e.getKey();
            Course c = courseRepository.findById(courseId).orElse(null);
            if (c == null) continue;
            List<Lesson> lessons = lessonRepository.findByCourseIdOrderBySortOrderAsc(c.getId());
            Map<Long, StudyProgress> merged = progressBufferService.loadCourseProgress(userId, c.getId(), lessons);
            StudyProgress latest = merged.values().stream()
                    .max(Comparator.comparing(x -> Optional.ofNullable(x.getUpdatedAt()).orElse(LocalDateTime.MIN)))
                    .orElse(e.getValue());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("lastProgress", latest);
            row.put("course", c);
            long done = merged.values().stream().filter(x -> Boolean.TRUE.equals(x.getCompleted())).count();
            row.put("completedLessons", done);
            row.put("totalLessons", lessons.size());
            double pct = lessons.isEmpty() ? 0 : done * 100.0 / lessons.size();
            row.put("progressPercent", Math.round(pct * 10) / 10.0);

            Lesson continueLesson = null;
            for (Lesson l : lessons) {
                StudyProgress lp = merged.get(l.getId());
                if (lp == null || !Boolean.TRUE.equals(lp.getCompleted())) {
                    continueLesson = l;
                    break;
                }
            }
            if (continueLesson == null && !lessons.isEmpty()) {
                continueLesson = lessons.get(lessons.size() - 1);
            }
            if (continueLesson != null) {
                row.put("continueLessonId", continueLesson.getId());
                row.put("lesson", continueLesson);
                StudyProgress cp = merged.get(continueLesson.getId());
                row.put("continueCompleted", cp != null && Boolean.TRUE.equals(cp.getCompleted()));
            } else if (latest != null) {
                lessonRepository.findById(latest.getLessonId()).ifPresent(l -> row.put("lesson", l));
                row.put("continueLessonId", latest.getLessonId());
            }
            out.add(row);
        }
        out.sort((a, b) -> {
            StudyProgress pa = (StudyProgress) a.get("lastProgress");
            StudyProgress pb = (StudyProgress) b.get("lastProgress");
            LocalDateTime ta = pa != null && pa.getUpdatedAt() != null ? pa.getUpdatedAt() : LocalDateTime.MIN;
            LocalDateTime tb = pb != null && pb.getUpdatedAt() != null ? pb.getUpdatedAt() : LocalDateTime.MIN;
            return tb.compareTo(ta);
        });
        return out;
    }

    @Transactional
    public StudyNote addNote(UserPrincipal me, NoteReq req) {
        Lesson lesson = lessonRepository.findById(req.getLessonId())
                .orElseThrow(() -> new BusinessException("课时不存在"));
        StudyNote n = StudyNote.builder()
                .userId(me.getId())
                .courseId(lesson.getCourseId())
                .lessonId(lesson.getId())
                .positionSeconds(req.getPositionSeconds())
                .content(req.getContent())
                .build();
        return noteRepository.save(n);
    }

    public List<StudyNote> notes(Long userId, Long lessonId) {
        if (lessonId != null) return noteRepository.findByUserIdAndLessonIdOrderByPositionSecondsAsc(userId, lessonId);
        return noteRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public void deleteNote(UserPrincipal me, Long id) {
        StudyNote n = noteRepository.findById(id).orElseThrow(() -> new BusinessException("笔记不存在"));
        if (!n.getUserId().equals(me.getId())) throw new BusinessException(403, "无权删除");
        noteRepository.delete(n);
    }

    @Transactional
    public Bookmark addBookmark(UserPrincipal me, BookmarkReq req) {
        Lesson lesson = lessonRepository.findById(req.getLessonId())
                .orElseThrow(() -> new BusinessException("课时不存在"));
        Bookmark b = Bookmark.builder()
                .userId(me.getId())
                .courseId(lesson.getCourseId())
                .lessonId(lesson.getId())
                .positionSeconds(req.getPositionSeconds())
                .title(req.getTitle() == null ? "书签 @" + req.getPositionSeconds() + "s" : req.getTitle())
                .build();
        return bookmarkRepository.save(b);
    }

    public List<Bookmark> bookmarks(Long userId, Long lessonId) {
        if (lessonId != null) return bookmarkRepository.findByUserIdAndLessonIdOrderByPositionSecondsAsc(userId, lessonId);
        return bookmarkRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public void deleteBookmark(UserPrincipal me, Long id) {
        Bookmark b = bookmarkRepository.findById(id).orElseThrow(() -> new BusinessException("书签不存在"));
        if (!b.getUserId().equals(me.getId())) throw new BusinessException(403, "无权删除");
        bookmarkRepository.delete(b);
    }
}
