package com.vlp.learn.async;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vlp.cert.service.CertificateService;
import com.vlp.common.BusinessException;
import com.vlp.content.entity.Course;
import com.vlp.content.entity.Lesson;
import com.vlp.content.repo.CourseRepository;
import com.vlp.content.repo.LessonRepository;
import com.vlp.learn.dto.LearnDtos.ProgressReport;
import com.vlp.learn.entity.StudyProgress;
import com.vlp.learn.repo.StudyProgressRepository;
import com.vlp.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Playback position is fully ephemeral in Redis only.
 * <p>
 * Heartbeats (~3s) never flush position to MySQL. Losing progress on Redis restart
 * or TTL expiry is ACCEPTABLE — resume from 0 or whatever Redis still has.
 * <p>
 * Lesson completion is a rare sparse MySQL write (once) so certificates / admin
 * completion stats still work. Live position, my-learning %, and continueLessonId
 * prefer Redis; Redis miss ⇒ treat as no progress (0).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProgressBufferService {
    public static final String KEY_PREFIX = "vlp:progress:";
    /** SET of lessonId strings for a user — listing my-learning / continue without DB. */
    public static final String USER_INDEX_PREFIX = "vlp:progress:idx:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final StudyProgressRepository progressRepository;
    private final LessonRepository lessonRepository;
    private final CourseRepository courseRepository;
    private final CertificateService certificateService;

    /** Kept for config compatibility; when false, accept() still uses Redis-only (no MySQL heartbeats). */
    @Value("${vlp.learn.progress-async-enabled:true}")
    private boolean asyncEnabled;

    @Value("${vlp.learn.progress-max-jump-seconds:120}")
    private int maxJump;

    @Value("${vlp.learn.completion-ratio:0.9}")
    private double defaultCompletionRatio;

    /** Idle progress keys expire; default 14d within product window 7–30d. */
    @Value("${vlp.learn.progress-ttl-days:14}")
    private int ttlDays;

    public boolean isAsyncEnabled() {
        return asyncEnabled;
    }

    private Duration ttl() {
        int days = Math.max(1, Math.min(ttlDays, 90));
        return Duration.ofDays(days);
    }

    private String progressKey(Long userId, Long lessonId) {
        return KEY_PREFIX + userId + ":" + lessonId;
    }

    private String userIndexKey(Long userId) {
        return USER_INDEX_PREFIX + userId;
    }

    /**
     * Redis-only course progress map. Does not read MySQL for live position.
     * Miss ⇒ empty / absent lesson = no progress (0).
     */
    public Map<Long, StudyProgress> loadCourseProgress(Long userId, Long courseId, List<Lesson> lessons) {
        Map<Long, StudyProgress> map = new LinkedHashMap<>();
        if (lessons == null || lessons.isEmpty()) return map;
        try {
            for (Lesson lesson : lessons) {
                loadBuffered(progressKey(userId, lesson.getId())).ifPresent(p -> {
                    if (courseId == null || courseId.equals(p.getCourseId())) {
                        map.put(lesson.getId(), p);
                    }
                });
            }
        } catch (Exception e) {
            log.debug("loadCourseProgress skipped: {}", e.getMessage());
        }
        return map;
    }

    /**
     * @deprecated Prefer {@link #loadCourseProgress}. DB rows are ignored for live position;
     * kept so call sites compile during migration — overlays Redis only.
     */
    @Deprecated
    public Map<Long, StudyProgress> mergeCourseProgress(Long userId, Long courseId, List<StudyProgress> dbRowsIgnored) {
        List<Lesson> lessons = lessonRepository.findByCourseIdOrderBySortOrderAsc(courseId);
        return loadCourseProgress(userId, courseId, lessons);
    }

    /** All Redis progress entries for a user (via index SET), newest activity first. */
    public List<StudyProgress> listUserProgress(Long userId) {
        List<StudyProgress> out = new ArrayList<>();
        try {
            Set<String> lessonIds = redis.opsForSet().members(userIndexKey(userId));
            if (lessonIds == null || lessonIds.isEmpty()) return out;
            for (String lid : lessonIds) {
                try {
                    Long lessonId = Long.parseLong(lid);
                    loadBuffered(progressKey(userId, lessonId)).ifPresent(out::add);
                } catch (NumberFormatException ignored) {}
            }
            out.sort(Comparator.comparing(
                    (StudyProgress p) -> Optional.ofNullable(p.getUpdatedAt()).orElse(LocalDateTime.MIN)
            ).reversed());
        } catch (Exception e) {
            log.debug("listUserProgress skipped: {}", e.getMessage());
        }
        return out;
    }

    public StudyProgress accept(UserPrincipal me, ProgressReport req) {
        Lesson lesson = lessonRepository.findById(req.getLessonId())
                .orElseThrow(() -> new BusinessException("课时不存在"));
        Course course = courseRepository.findById(lesson.getCourseId())
                .orElseThrow(() -> new BusinessException("课程不存在"));

        String key = progressKey(me.getId(), lesson.getId());
        // Redis only — no MySQL seed for live position (miss ⇒ fresh 0)
        StudyProgress p = loadBuffered(key).orElseGet(() -> StudyProgress.builder()
                .userId(me.getId())
                .courseId(lesson.getCourseId())
                .lessonId(lesson.getId())
                .build());

        boolean wasCompleted = Boolean.TRUE.equals(p.getCompleted());

        int pos = Math.max(0, req.getPositionSeconds());
        int maxPos = Optional.ofNullable(p.getMaxPositionSeconds()).orElse(0);
        boolean ended = Boolean.TRUE.equals(req.getEnded());
        if (!ended && pos > maxPos + maxJump) {
            pos = maxPos;
        }
        if (pos > maxPos) maxPos = pos;
        if (ended && req.getVideoDurationSeconds() != null && req.getVideoDurationSeconds() > maxPos) {
            maxPos = req.getVideoDurationSeconds();
            pos = Math.max(pos, maxPos);
        }

        p.setUserId(me.getId());
        p.setCourseId(lesson.getCourseId());
        p.setLessonId(lesson.getId());
        p.setPositionSeconds(pos);
        p.setMaxPositionSeconds(maxPos);
        if (req.getDeltaSeconds() != null && req.getDeltaSeconds() > 0) {
            int delta = Math.min(req.getDeltaSeconds(), 60);
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
        if (shouldComplete && !wasCompleted) {
            p.setCompleted(true);
            p.setCompletedAt(LocalDateTime.now());
            if (pos < maxPos) p.setPositionSeconds(maxPos);
        }
        p.setUpdatedAt(LocalDateTime.now());

        Duration keyTtl = ttl();
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(p), keyTtl);
            String idx = userIndexKey(me.getId());
            redis.opsForSet().add(idx, String.valueOf(lesson.getId()));
            redis.expire(idx, keyTtl);
        } catch (Exception e) {
            // No MySQL fallback for heartbeats — progress is ephemeral by product decision.
            log.warn("progress redis write failed (no MySQL fallback): {}", e.getMessage());
            throw new BusinessException(503, "进度服务暂时不可用，请稍后重试");
        }

        // Sparse one-shot completion row for certs / admin (not position heartbeats)
        if (Boolean.TRUE.equals(p.getCompleted()) && !wasCompleted) {
            persistCompletionSparse(p);
            tryMaybeCert(me.getId(), course);
        }
        return p;
    }

    /**
     * Rare explicit write: lesson completion flag only (position remains Redis-authoritative).
     * Idempotent upsert on (userId, lessonId).
     */
    protected void persistCompletionSparse(StudyProgress buffered) {
        try {
            StudyProgress db = progressRepository
                    .findByUserIdAndLessonId(buffered.getUserId(), buffered.getLessonId())
                    .orElseGet(() -> StudyProgress.builder()
                            .userId(buffered.getUserId())
                            .courseId(buffered.getCourseId())
                            .lessonId(buffered.getLessonId())
                            .build());
            db.setCompleted(true);
            db.setCompletedAt(buffered.getCompletedAt() != null ? buffered.getCompletedAt() : LocalDateTime.now());
            // Snapshot max for admin CSV only; live resume still uses Redis
            db.setMaxPositionSeconds(buffered.getMaxPositionSeconds());
            db.setPositionSeconds(buffered.getPositionSeconds());
            db.setStudiedSeconds(buffered.getStudiedSeconds());
            progressRepository.save(db);
        } catch (Exception e) {
            log.warn("sparse completion persist failed uid={} lesson={}: {}",
                    buffered.getUserId(), buffered.getLessonId(), e.getMessage());
        }
    }

    private Optional<StudyProgress> loadBuffered(String key) {
        try {
            String json = redis.opsForValue().get(key);
            if (json == null || json.isBlank()) return Optional.empty();
            return Optional.of(objectMapper.readValue(json, StudyProgress.class));
        } catch (Exception e) {
            return Optional.empty();
        }
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

    /** Cert check uses Redis completion flags for the course (not waiting for a flush). */
    private void tryMaybeCert(Long userId, Course course) {
        try {
            List<Lesson> lessons = lessonRepository.findByCourseIdOrderBySortOrderAsc(course.getId());
            if (lessons.isEmpty()) return;
            Map<Long, StudyProgress> redisMap = loadCourseProgress(userId, course.getId(), lessons);
            long done = redisMap.values().stream().filter(x -> Boolean.TRUE.equals(x.getCompleted())).count();
            double rate = done * 1.0 / lessons.size();
            double need = course.getCompletionRatio() != null ? course.getCompletionRatio() : defaultCompletionRatio;
            if (rate >= need) {
                certificateService.issueIfAbsent(userId, course, rate);
            }
        } catch (Exception e) {
            log.debug("cert check deferred: {}", e.getMessage());
        }
    }
}
