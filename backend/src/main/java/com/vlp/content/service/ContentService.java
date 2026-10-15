package com.vlp.content.service;

import com.vlp.common.BusinessException;
import com.vlp.common.PageResult;
import com.vlp.content.entity.*;
import com.vlp.content.repo.*;
import com.vlp.cache.PublicContentCache;
import com.vlp.learn.async.ProgressBufferService;
import com.vlp.learn.entity.StudyProgress;
import com.vlp.learn.repo.StudyProgressRepository;
import com.vlp.upload.MediaSigningService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ContentService {
    private final NewsRepository newsRepository;
    private final CategoryRepository categoryRepository;
    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;
    private final BannerRepository bannerRepository;
    private final StudyProgressRepository progressRepository;
    private final ProgressBufferService progressBufferService;
    private final PublicContentCache publicContentCache;
    private final MediaSigningService mediaSigningService;

    public Map<String, Object> home(Long userId) {
        Map<String, Object> m = new LinkedHashMap<>(publicContentCache.homeAnonPayload());
        if (userId != null) {
            List<StudyProgress> recent = progressRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream().limit(5).toList();
            m.put("continueLearning", enrichProgress(recent));
        } else {
            m.put("continueLearning", List.of());
        }
        return m;
    }

    private List<Map<String, Object>> enrichProgress(List<StudyProgress> list) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (StudyProgress p : list) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("progress", p);
            courseRepository.findById(p.getCourseId()).ifPresent(c -> row.put("course", c));
            lessonRepository.findById(p.getLessonId()).ifPresent(l -> row.put("lesson", l));
            out.add(row);
        }
        return out;
    }

    public PageResult<News> newsList(int page, int size) {
        return PageResult.of(newsRepository.findByStatusOrderByPublishedAtDesc("PUBLISHED", PageRequest.of(page - 1, size)));
    }

    public News newsDetail(Long id) {
        News n = newsRepository.findById(id).orElseThrow(() -> new BusinessException("新闻不存在"));
        if (!"PUBLISHED".equals(n.getStatus())) throw new BusinessException("新闻未发布");
        n.setViewCount(Optional.ofNullable(n.getViewCount()).orElse(0) + 1);
        return newsRepository.save(n);
    }

    public List<Category> categories() {
        return publicContentCache.categories();
    }

    public PageResult<Course> courseList(Long categoryId, String keyword, int page, int size) {
        return PageResult.of(courseRepository.searchPublished("PUBLISHED", categoryId, keyword, PageRequest.of(page - 1, size)));
    }

    public Map<String, Object> courseDetail(Long id, Long userId) {
        Course c = courseRepository.findById(id).orElseThrow(() -> new BusinessException("课程不存在"));
        if (!"PUBLISHED".equals(c.getStatus())) throw new BusinessException("课程未上架");
        List<Lesson> lessons = lessonRepository.findByCourseIdOrderBySortOrderAsc(id);
        // Keep videoPath as opaque storage key only — never permanent public OSS/CDN HTTP.
        // Playback URL is minted via GET /api/learn/play-url/{lessonId} (JWT + short TTL).
        for (Lesson l : lessons) {
            if (l.getVideoPath() != null) {
                l.setVideoPath(mediaSigningService.opaqueVideoKey(l.getVideoPath()));
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("course", c);
        m.put("lessons", lessons);
        if (userId != null) {
            List<StudyProgress> dbRows = progressRepository.findByUserIdAndCourseId(userId, id);
            Map<Long, StudyProgress> map = progressBufferService.mergeCourseProgress(userId, id, dbRows);
            // stringify keys for stable frontend lookup (JSON Long keys become strings anyway)
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
}
