package com.vlp.cache;

import com.vlp.content.entity.Category;
import com.vlp.content.repo.BannerRepository;
import com.vlp.content.repo.CategoryRepository;
import com.vlp.content.repo.CourseRepository;
import com.vlp.content.repo.NewsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PublicContentCache {
    private final BannerRepository bannerRepository;
    private final CourseRepository courseRepository;
    private final NewsRepository newsRepository;
    private final CategoryRepository categoryRepository;

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
}
