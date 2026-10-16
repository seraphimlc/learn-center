package com.vlp;

import com.vlp.cache.PublicContentCache;
import com.vlp.common.PageResult;
import com.vlp.content.entity.Course;
import com.vlp.content.entity.Lesson;
import com.vlp.content.repo.CourseRepository;
import com.vlp.content.repo.LessonRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PublicContentCacheIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired PublicContentCache publicContentCache;
    @Autowired CourseRepository courseRepository;
    @Autowired LessonRepository lessonRepository;
    @Autowired StringRedisTemplate redis;

    @BeforeEach
    void clearPublicCaches() {
        publicContentCache.evictAllPublic();
    }

    @Test
    void homeAndCourseListAreServedAndCached() throws Exception {
        mockMvc.perform(get("/api/public/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.banners").isArray())
                .andExpect(jsonPath("$.data.categories").isArray());

        mockMvc.perform(get("/api/public/courses").param("page", "1").param("size", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.list").isArray());

        Map<String, Object> home1 = publicContentCache.homeAnonPayload();
        Map<String, Object> home2 = publicContentCache.homeAnonPayload();
        assertThat(home1).isNotNull();
        assertThat(home2).containsKeys("banners", "hotCourses", "categories");

        PageResult<Course> page1 = publicContentCache.courseList(null, null, 1, 12);
        PageResult<Course> page2 = publicContentCache.courseList(null, null, 1, 12);
        assertThat(page1.getPage()).isEqualTo(1);
        assertThat(page2.getTotal()).isEqualTo(page1.getTotal());

        Set<String> keys = redis.keys("*homeAnon*");
        Set<String> keys2 = redis.keys("*courseList*");
        assertThat(keys != null && !keys.isEmpty() || keys2 != null && !keys2.isEmpty())
                .as("expected spring cache keys, home=%s courseList=%s", keys, keys2)
                .isTrue();
    }

    @Test
    void courseDetailCacheThenOverlayWorksViaApi() throws Exception {
        Course c = courseRepository.save(Course.builder()
                .title("缓存课-" + System.currentTimeMillis())
                .status("PUBLISHED")
                .summary("cache")
                .completionRatio(0.9)
                .build());
        lessonRepository.save(Lesson.builder()
                .courseId(c.getId())
                .title("L1")
                .videoPath("videos/cache.mp4")
                .durationSeconds(60)
                .sortOrder(1)
                .build());

        Map<String, Object> shell = publicContentCache.coursePublicDetail(c.getId());
        assertThat(shell).isNotNull();
        assertThat(shell.get("course")).isNotNull();
        assertThat(shell.get("lessons")).asList().isNotEmpty();

        Map<String, Object> again = publicContentCache.coursePublicDetail(c.getId());
        assertThat(((Course) again.get("course")).getId()).isEqualTo(c.getId());

        mockMvc.perform(get("/api/public/courses/" + c.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.course.id").value(c.getId()))
                .andExpect(jsonPath("$.data.lessons").isArray());

        publicContentCache.evictAllPublic();
        assertThat(publicContentCache.courseById(c.getId())).isNotNull();
    }
}
