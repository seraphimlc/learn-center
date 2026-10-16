package com.vlp.cache;

import com.vlp.common.PageResult;
import com.vlp.content.entity.*;
import com.vlp.content.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Redis-backed public / semi-static content reads.
 * Progress / continueLearning stay out of these keys (Redis progress buffer is separate).
 * Admin writes call {@link #evictAllPublic()} — short TTLs are the safety net.
 */
@Service
@RequiredArgsConstructor
public class PublicContentCache {
    private final BannerRepository bannerRepository;
    private final CourseRepository courseRepository;
    private final NewsRepository newsRepository;
    private final CategoryRepository categoryRepository;
    private final LessonRepository lessonRepository;
    private final SiteConfigRepository siteConfigRepository;

    @Cacheable(cacheNames = "homeAnon", key = "'v1'")
    public Map<String, Object> homeAnonPayload() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("banners", bannerRepository.findByEnabledTrueOrderBySortOrderAsc());
        m.put("hotCourses", courseRepository.findTop8ByStatusAndHotTrueOrderBySortOrderAsc("PUBLISHED"));
        m.put("recommendCourses", courseRepository.findTop8ByStatusAndRecommendedTrueOrderBySortOrderAsc("PUBLISHED"));
        m.put("news", newsRepository.findByStatusOrderByPublishedAtDesc("PUBLISHED", PageRequest.of(0, 5)).getContent());
        m.put("categories", categoryRepository.findByEnabledTrueOrderBySortOrderAsc());
        return m;
    }

    @Cacheable(cacheNames = "categories", key = "'enabled'")
    public List<Category> categories() {
        return categoryRepository.findByEnabledTrueOrderBySortOrderAsc();
    }

    @Cacheable(cacheNames = "banners", key = "'enabled'")
    public List<Banner> bannersEnabled() {
        return bannerRepository.findByEnabledTrueOrderBySortOrderAsc();
    }

    @Cacheable(cacheNames = "siteConfig", key = "'all'")
    public List<SiteConfig> siteConfigs() {
        return siteConfigRepository.findAll();
    }

    public Optional<String> siteConfigValue(String key) {
        return siteConfigs().stream()
                .filter(c -> key.equals(c.getConfigKey()))
                .map(SiteConfig::getConfigValue)
                .findFirst();
    }

    @Cacheable(cacheNames = "newsList",
            key = "#page + ':' + #size")
    public PageResult<News> newsList(int page, int size) {
        return PageResult.of(newsRepository.findByStatusOrderByPublishedAtDesc(
                "PUBLISHED", PageRequest.of(page - 1, size)));
    }

    /**
     * Published course list. Keyword is part of the key; blank keyword is the hot path
     * (browse / category chips). Short TTL limits pollution from rare search terms.
     */
    @Cacheable(cacheNames = "courseList",
            key = "T(java.util.Objects).toString(#categoryId) + ':' + (#keyword == null || #keyword.isBlank() ? '' : #keyword.trim()) + ':' + #page + ':' + #size")
    public PageResult<Course> courseList(Long categoryId, String keyword, int page, int size) {
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        return PageResult.of(courseRepository.searchPublished(
                "PUBLISHED", categoryId, kw, PageRequest.of(page - 1, size)));
    }

    /**
     * Public course + ordered lessons (no user progress). Caller overlays Redis progress.
     */
    @Cacheable(cacheNames = "courseDetail", key = "#courseId")
    public Map<String, Object> coursePublicDetail(Long courseId) {
        Course c = courseRepository.findById(courseId).orElse(null);
        if (c == null || !"PUBLISHED".equals(c.getStatus())) {
            return null;
        }
        List<Lesson> lessons = lessonRepository.findByCourseIdOrderBySortOrderAsc(courseId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("course", c);
        m.put("lessons", lessons);
        return m;
    }

    @Cacheable(cacheNames = "courseLessons", key = "#courseId")
    public List<Lesson> lessonsByCourse(Long courseId) {
        return lessonRepository.findByCourseIdOrderBySortOrderAsc(courseId);
    }

    @Cacheable(cacheNames = "courseById", key = "#courseId")
    public Course courseById(Long courseId) {
        return courseRepository.findById(courseId).orElse(null);
    }

    @Cacheable(cacheNames = "lessonById", key = "#lessonId")
    public Lesson lessonById(Long lessonId) {
        return lessonRepository.findById(lessonId).orElse(null);
    }

    /** Broad invalidation after admin content/config writes. TTLs remain the fallback. */
    @Caching(evict = {
            @CacheEvict(cacheNames = "homeAnon", allEntries = true),
            @CacheEvict(cacheNames = "categories", allEntries = true),
            @CacheEvict(cacheNames = "banners", allEntries = true),
            @CacheEvict(cacheNames = "siteConfig", allEntries = true),
            @CacheEvict(cacheNames = "newsList", allEntries = true),
            @CacheEvict(cacheNames = "courseList", allEntries = true),
            @CacheEvict(cacheNames = "courseDetail", allEntries = true),
            @CacheEvict(cacheNames = "courseLessons", allEntries = true),
            @CacheEvict(cacheNames = "courseById", allEntries = true),
            @CacheEvict(cacheNames = "lessonById", allEntries = true)
    })
    public void evictAllPublic() {
        // annotation-driven
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "lessonById", key = "#lessonId"),
            @CacheEvict(cacheNames = "courseLessons", key = "#courseId"),
            @CacheEvict(cacheNames = "courseById", key = "#courseId"),
            @CacheEvict(cacheNames = "courseDetail", key = "#courseId"),
            @CacheEvict(cacheNames = "homeAnon", allEntries = true),
            @CacheEvict(cacheNames = "courseList", allEntries = true)
    })
    public void evictAfterLessonMetaChange(Long lessonId, Long courseId) {
        // duration sync / lesson edit
    }
}
