package com.vlp.learn.service;

import com.vlp.cert.service.CertificateService;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.Comparator;

@Service
@RequiredArgsConstructor
public class LearnService {
    private final StudyProgressRepository progressRepository;
    private final StudyNoteRepository noteRepository;
    private final BookmarkRepository bookmarkRepository;
    private final LessonRepository lessonRepository;
    private final CourseRepository courseRepository;
    private final CertificateService certificateService;
    private final ProgressBufferService progressBufferService;

    @Value("${vlp.learn.completion-ratio:0.9}")
    private double defaultCompletionRatio;

    @Value("${vlp.learn.progress-max-jump-seconds:120}")
    private int maxJump;

    @Transactional
    public StudyProgress reportProgress(UserPrincipal me, ProgressReport req) {
        if (progressBufferService.isAsyncEnabled()) {
            return progressBufferService.accept(me, req);
        }
        Lesson lesson = lessonRepository.findById(req.getLessonId())
                .orElseThrow(() -> new BusinessException("课时不存在"));
        Course course = courseRepository.findById(lesson.getCourseId())
                .orElseThrow(() -> new BusinessException("课程不存在"));

        StudyProgress p = progressRepository.findByUserIdAndLessonId(me.getId(), lesson.getId())
                .orElseGet(() -> StudyProgress.builder()
                        .userId(me.getId())
                        .courseId(lesson.getCourseId())
                        .lessonId(lesson.getId())
                        .build());

        int pos = Math.max(0, req.getPositionSeconds());
        int maxPos = Optional.ofNullable(p.getMaxPositionSeconds()).orElse(0);
        boolean ended = Boolean.TRUE.equals(req.getEnded());
        // anti-cheat: progress is monotonic; allow small jump for seek within maxJump of max
        if (!ended && pos > maxPos + maxJump) {
            pos = maxPos; // reject big forward jump
        }
        if (pos > maxPos) maxPos = pos;
        if (ended && req.getVideoDurationSeconds() != null && req.getVideoDurationSeconds() > maxPos) {
            maxPos = req.getVideoDurationSeconds();
            pos = Math.max(pos, maxPos);
        }

        p.setPositionSeconds(pos);
        p.setMaxPositionSeconds(maxPos);
        if (req.getDeltaSeconds() != null && req.getDeltaSeconds() > 0) {
            int delta = Math.min(req.getDeltaSeconds(), 60); // cap per report (3s heartbeat ≪ 60; blocks inflated deltas)
            p.setStudiedSeconds(Optional.ofNullable(p.getStudiedSeconds()).orElse(0) + delta);
        }
        if (Boolean.TRUE.equals(req.getHeartbeat())) {
            p.setLastHeartbeatAt(LocalDateTime.now());
        }

        int duration = syncLessonDuration(lesson, req.getVideoDurationSeconds());
        double ratio = course.getCompletionRatio() != null ? course.getCompletionRatio() : defaultCompletionRatio;
        boolean shouldComplete = Boolean.TRUE.equals(req.getEnded());
        if (!shouldComplete && duration > 0) {
            shouldComplete = maxPos >= (int) Math.ceil(duration * ratio);
        }
        if (shouldComplete && !Boolean.TRUE.equals(p.getCompleted())) {
            p.setCompleted(true);
            p.setCompletedAt(LocalDateTime.now());
            if (pos < maxPos) p.setPositionSeconds(maxPos);
        }
        progressRepository.save(p);

        maybeIssueCert(me.getId(), course);
        return p;
    }

    private void maybeIssueCert(Long userId, Course course) {
        List<Lesson> lessons = lessonRepository.findByCourseIdOrderBySortOrderAsc(course.getId());
        if (lessons.isEmpty()) return;
        List<StudyProgress> all = progressRepository.findByUserIdAndCourseId(userId, course.getId());
        long done = all.stream().filter(x -> Boolean.TRUE.equals(x.getCompleted())).count();
        double rate = done * 1.0 / lessons.size();
        double need = course.getCompletionRatio() != null ? course.getCompletionRatio() : defaultCompletionRatio;
        if (rate >= need) {
            certificateService.issueIfAbsent(userId, course, rate);
        }
    }

    public List<Map<String, Object>> myLearning(Long userId) {
        List<StudyProgress> list = progressRepository.findByUserIdOrderByUpdatedAtDesc(userId);
        Map<Long, StudyProgress> latestByCourse = new LinkedHashMap<>();
        for (StudyProgress p : list) {
            latestByCourse.putIfAbsent(p.getCourseId(), p);
        }
        // also surface courses that only exist in redis buffer
        Set<Long> courseIds = new LinkedHashSet<>(latestByCourse.keySet());
        List<Map<String, Object>> out = new ArrayList<>();
        for (Long courseId : courseIds) {
            StudyProgress p = latestByCourse.get(courseId);
            Map<String, Object> row = new LinkedHashMap<>();
            Course c = courseRepository.findById(courseId).orElse(null);
            if (c == null) continue;
            List<Lesson> lessons = lessonRepository.findByCourseIdOrderBySortOrderAsc(c.getId());
            List<StudyProgress> dbRows = progressRepository.findByUserIdAndCourseId(userId, c.getId());
            Map<Long, StudyProgress> merged = progressBufferService.mergeCourseProgress(userId, c.getId(), dbRows);
            // keep latest activity ordering using merged updatedAt
            StudyProgress latest = merged.values().stream()
                    .max(Comparator.comparing(x -> Optional.ofNullable(x.getUpdatedAt()).orElse(LocalDateTime.MIN)))
                    .orElse(p);
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
                continueLesson = lessons.get(lessons.size() - 1); // all done → last lesson
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
        // sort by latest activity
        out.sort((a, b) -> {
            StudyProgress pa = (StudyProgress) a.get("lastProgress");
            StudyProgress pb = (StudyProgress) b.get("lastProgress");
            LocalDateTime ta = pa != null && pa.getUpdatedAt() != null ? pa.getUpdatedAt() : LocalDateTime.MIN;
            LocalDateTime tb = pb != null && pb.getUpdatedAt() != null ? pb.getUpdatedAt() : LocalDateTime.MIN;
            return tb.compareTo(ta);
        });
        return out;
    }

    private int syncLessonDuration(Lesson lesson, Integer videoDurationSeconds) {
        int duration = Optional.ofNullable(lesson.getDurationSeconds()).orElse(0);
        if (videoDurationSeconds == null || videoDurationSeconds <= 0) return duration;
        int reported = videoDurationSeconds;
        if (duration <= 0 || Math.abs(duration - reported) >= 5) {
            lesson.setDurationSeconds(reported);
            lessonRepository.save(lesson);
            courseRepository.findById(lesson.getCourseId()).ifPresent(c -> {
                List<Lesson> list = lessonRepository.findByCourseIdOrderBySortOrderAsc(c.getId());
                c.setDurationSeconds(list.stream().mapToInt(x -> Optional.ofNullable(x.getDurationSeconds()).orElse(0)).sum());
                c.setLessonCount(list.size());
                courseRepository.save(c);
            });
            return reported;
        }
        return duration;
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
