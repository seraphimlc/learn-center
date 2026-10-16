package com.vlp.content.service;

import com.vlp.common.BusinessException;
import com.vlp.common.PageResult;
import com.vlp.content.entity.*;
import com.vlp.content.repo.*;
import com.vlp.cache.PublicContentCache;
import com.vlp.learn.async.ProgressBufferService;
import com.vlp.learn.entity.StudyProgress;
import com.vlp.upload.MediaSigningService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ContentService {
    private final NewsRepository newsRepository;
    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;
    private final ProgressBufferService progressBufferService;
    private final PublicContentCache publicContentCache;
    private final MediaSigningService mediaSigningService;

    public Map<String, Object> home(Long userId) {
        Map<String, Object> m = new LinkedHashMap<>(publicContentCache.homeAnonPayload());
        if (userId != null) {
            List<StudyProgress> recent = progressBufferService.listUserProgress(userId).stream().limit(5).toList();
            m.put("continueLearning", enrichProgress(recent));
        } else {
            m.put("continueLearning", List.of());
        }
        return m;
    }

    /** Batch-load course/lesson rows — avoids N+1 on home continueLearning. */
    private List<Map<String, Object>> enrichProgress(List<StudyProgress> list) {
        if (list == null || list.isEmpty()) return List.of();
        Set<Long> courseIds = list.stream().map(StudyProgress::getCourseId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> lessonIds = list.stream().map(StudyProgress::getLessonId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Course> courses = courseRepository.findAllById(courseIds).stream()
                .collect(Collectors.toMap(Course::getId, Function.identity()));
        Map<Long, Lesson> lessons = lessonRepository.findAllById(lessonIds).stream()
                .collect(Collectors.toMap(Lesson::getId, Function.identity()));
        List<Map<String, Object>> out = new ArrayList<>();
        for (StudyProgress p : list) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("progress", p);
            Course c = courses.get(p.getCourseId());
            if (c != null) row.put("course", c);
            Lesson l = lessons.get(p.getLessonId());
            if (l != null) row.put("lesson", l);
            out.add(row);
        }
        return out;
    }

    public PageResult<News> newsList(int page, int size) {
        return publicContentCache.newsList(page, size);
    }

    public News newsDetail(Long id) {
        // viewCount++ is a write — do not cache this path
        News n = newsRepository.findById(id).orElseThrow(() -> new BusinessException("新闻不存在"));
        if (!"PUBLISHED".equals(n.getStatus())) throw new BusinessException("新闻未发布");
        n.setViewCount(Optional.ofNullable(n.getViewCount()).orElse(0) + 1);
        return newsRepository.save(n);
    }

    public List<Category> categories() {
        return publicContentCache.categories();
    }

    public PageResult<Course> courseList(Long categoryId, String keyword, int page, int size) {
        return publicContentCache.courseList(categoryId, keyword, page, size);
    }

    public Map<String, Object> courseDetail(Long id, Long userId) {
        Map<String, Object> cached = publicContentCache.coursePublicDetail(id);
        if (cached == null) {
            Course c = courseRepository.findById(id).orElseThrow(() -> new BusinessException("课程不存在"));
            if (!"PUBLISHED".equals(c.getStatus())) throw new BusinessException("课程未上架");
            throw new BusinessException("课程未上架");
        }
        Course c = (Course) cached.get("course");
        @SuppressWarnings("unchecked")
        List<Lesson> lessonsRaw = (List<Lesson>) cached.get("lessons");
        // Defensive copy + opaque keys (never mutate a value we might confuse with cache identity)
        List<Lesson> lessons = new ArrayList<>();
        if (lessonsRaw != null) {
            for (Lesson src : lessonsRaw) {
                Lesson l = copyLesson(src);
                if (l.getVideoPath() != null) {
                    l.setVideoPath(mediaSigningService.opaqueVideoKey(l.getVideoPath()));
                }
                lessons.add(l);
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("course", c);
        m.put("lessons", lessons);
        if (userId != null) {
            // Redis-only live position; miss ⇒ no progress (0)
            Map<Long, StudyProgress> map = progressBufferService.loadCourseProgress(userId, id, lessons);
            Map<String, StudyProgress> front = new LinkedHashMap<>();
            map.forEach((k, v) -> front.put(String.valueOf(k), v));
            m.put("progressMap", front);
            long completed = map.values().stream().filter(p -> Boolean.TRUE.equals(p.getCompleted())).count();
            m.put("completedLessons", completed);
            double pct = lessons.isEmpty() ? 0 : completed * 100.0 / lessons.size();
            m.put("courseProgress", Math.round(pct * 10) / 10.0);
            Lesson continueLesson = null;
            for (Lesson l : lessons) {
                StudyProgress lp = map.get(l.getId());
                if (lp == null || !Boolean.TRUE.equals(lp.getCompleted())) {
                    continueLesson = l;
                    break;
                }
            }
            if (continueLesson == null && !lessons.isEmpty()) continueLesson = lessons.get(lessons.size() - 1);
            if (continueLesson != null) m.put("continueLessonId", continueLesson.getId());
        }
        return m;
    }

    private static Lesson copyLesson(Lesson src) {
        return Lesson.builder()
                .id(src.getId())
                .courseId(src.getCourseId())
                .title(src.getTitle())
                .description(src.getDescription())
                .videoPath(src.getVideoPath())
                .durationSeconds(src.getDurationSeconds())
                .sortOrder(src.getSortOrder())
                .freePreview(src.getFreePreview())
                .createdAt(src.getCreatedAt())
                .updatedAt(src.getUpdatedAt())
                .build();
    }
}
