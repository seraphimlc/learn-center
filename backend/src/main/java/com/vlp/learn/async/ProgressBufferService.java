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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * High-QPS progress path: merge into Redis, mark dirty, batch flush to MySQL.
 * Target: absorb ~10k concurrent ~3s heartbeat writers without saturating DB.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProgressBufferService {
    public static final String KEY_PREFIX = "vlp:progress:";
    public static final String DIRTY_SET = "vlp:progress:dirty";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final StudyProgressRepository progressRepository;
    private final LessonRepository lessonRepository;
    private final CourseRepository courseRepository;
    private final CertificateService certificateService;

    @Value("${vlp.learn.progress-async-enabled:true}")
    private boolean asyncEnabled;

    @Value("${vlp.learn.progress-max-jump-seconds:120}")
    private int maxJump;

    @Value("${vlp.learn.completion-ratio:0.9}")
    private double defaultCompletionRatio;

    @Value("${vlp.learn.progress-flush-batch-size:200}")
    private int batchSize;

    public boolean isAsyncEnabled() {
        return asyncEnabled;
    }

    /** Merge Redis-buffered progress over DB rows for a user+course (read path). */
    public Map<Long, StudyProgress> mergeCourseProgress(Long userId, Long courseId, List<StudyProgress> dbRows) {
        Map<Long, StudyProgress> map = new LinkedHashMap<>();
        for (StudyProgress p : dbRows) {
            map.put(p.getLessonId(), p);
        }
        if (!asyncEnabled) return map;
        try {
            for (Long lessonId : new ArrayList<>(map.keySet())) {
                overlayBuffer(userId, lessonId, map);
            }
            // also pick up brand-new buffered lessons not yet in DB
            Set<String> dirty = redis.opsForSet().members(DIRTY_SET);
            if (dirty != null) {
                String prefix = KEY_PREFIX + userId + ":";
                for (String key : dirty) {
                    if (!key.startsWith(prefix)) continue;
                    String lid = key.substring(prefix.length());
                    try {
                        Long lessonId = Long.parseLong(lid);
                        if (!map.containsKey(lessonId)) {
                            overlayBuffer(userId, lessonId, map);
                            StudyProgress p = map.get(lessonId);
                            if (p != null && !courseId.equals(p.getCourseId())) {
                                map.remove(lessonId);
                            }
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        } catch (Exception e) {
            log.debug("mergeCourseProgress skipped: {}", e.getMessage());
        }
        return map;
    }

    private void overlayBuffer(Long userId, Long lessonId, Map<Long, StudyProgress> map) {
        String key = KEY_PREFIX + userId + ":" + lessonId;
        loadBuffered(key).ifPresent(buf -> {
            if (buf.getCourseId() != null) {
                map.put(lessonId, buf);
            }
        });
    }


    public StudyProgress accept(UserPrincipal me, ProgressReport req) {
        Lesson lesson = lessonRepository.findById(req.getLessonId())
                .orElseThrow(() -> new BusinessException("课时不存在"));
        Course course = courseRepository.findById(lesson.getCourseId())
                .orElseThrow(() -> new BusinessException("课程不存在"));

        String key = KEY_PREFIX + me.getId() + ":" + lesson.getId();
        StudyProgress p = loadBuffered(key).orElseGet(() ->
                progressRepository.findByUserIdAndLessonId(me.getId(), lesson.getId())
                        .orElseGet(() -> StudyProgress.builder()
                                .userId(me.getId())
                                .courseId(lesson.getCourseId())
                                .lessonId(lesson.getId())
                                .build()));

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

        p.setPositionSeconds(pos);
        p.setMaxPositionSeconds(maxPos);
        if (req.getDeltaSeconds() != null && req.getDeltaSeconds() > 0) {
            int delta = Math.min(req.getDeltaSeconds(), 60); // anti-cheat: cap studied delta per report
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
        p.setUpdatedAt(LocalDateTime.now());

        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(p), Duration.ofDays(7));
            redis.opsForSet().add(DIRTY_SET, key);
        } catch (Exception e) {
            log.warn("progress redis buffer failed, sync fallback: {}", e.getMessage());
            return progressRepository.save(p);
        }

        // cert check can wait until flush; optimistic peek for UX
        if (Boolean.TRUE.equals(p.getCompleted())) {
            tryMaybeCert(me.getId(), course);
        }
        return p;
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

    @Scheduled(fixedDelayString = "${vlp.learn.progress-flush-interval-ms:2000}")
    @Transactional
    public void flushDirty() {
        if (!asyncEnabled) return;
        try {
            Set<String> keys = redis.opsForSet().distinctRandomMembers(DIRTY_SET, batchSize);
            if (keys == null || keys.isEmpty()) return;
            for (String key : keys) {
                try {
                    String json = redis.opsForValue().get(key);
                    if (json == null) {
                        redis.opsForSet().remove(DIRTY_SET, key);
                        continue;
                    }
                    StudyProgress buffered = objectMapper.readValue(json, StudyProgress.class);
                    StudyProgress db = progressRepository
                            .findByUserIdAndLessonId(buffered.getUserId(), buffered.getLessonId())
                            .orElseGet(() -> StudyProgress.builder()
                                    .userId(buffered.getUserId())
                                    .courseId(buffered.getCourseId())
                                    .lessonId(buffered.getLessonId())
                                    .build());
                    db.setPositionSeconds(buffered.getPositionSeconds());
                    db.setMaxPositionSeconds(buffered.getMaxPositionSeconds());
                    db.setStudiedSeconds(buffered.getStudiedSeconds());
                    db.setCompleted(buffered.getCompleted());
                    db.setCompletedAt(buffered.getCompletedAt());
                    db.setLastHeartbeatAt(buffered.getLastHeartbeatAt());
                    progressRepository.save(db);

                    // Avoid lost updates: only clear dirty if Redis value unchanged during flush;
                    // never overwrite a newer heartbeat with the snapshot we just persisted.
                    String latestJson = redis.opsForValue().get(key);
                    if (latestJson == null) {
                        redis.opsForSet().remove(DIRTY_SET, key);
                    } else if (latestJson.equals(json)) {
                        buffered.setId(db.getId());
                        redis.opsForValue().set(key, objectMapper.writeValueAsString(buffered), Duration.ofDays(7));
                        redis.opsForSet().remove(DIRTY_SET, key);
                    } else {
                        // Concurrent accept() wrote newer progress — stamp id, keep dirty for next flush
                        try {
                            StudyProgress latest = objectMapper.readValue(latestJson, StudyProgress.class);
                            if (latest.getId() == null) {
                                latest.setId(db.getId());
                                redis.opsForValue().set(key, objectMapper.writeValueAsString(latest), Duration.ofDays(7));
                            }
                        } catch (Exception ignore) { /* keep raw latest */ }
                        redis.opsForSet().add(DIRTY_SET, key);
                    }

                    if (Boolean.TRUE.equals(db.getCompleted())) {
                        courseRepository.findById(db.getCourseId()).ifPresent(c -> tryMaybeCert(db.getUserId(), c));
                    }
                } catch (Exception ex) {
                    log.warn("flush progress {} failed: {}", key, ex.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("progress flush skipped: {}", e.getMessage());
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

    private void tryMaybeCert(Long userId, Course course) {
        try {
            List<Lesson> lessons = lessonRepository.findByCourseIdOrderBySortOrderAsc(course.getId());
            if (lessons.isEmpty()) return;
            List<StudyProgress> all = progressRepository.findByUserIdAndCourseId(userId, course.getId());
            long done = all.stream().filter(x -> Boolean.TRUE.equals(x.getCompleted())).count();
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
