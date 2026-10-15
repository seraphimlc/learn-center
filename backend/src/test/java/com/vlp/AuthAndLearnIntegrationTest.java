
package com.vlp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vlp.content.entity.Course;
import com.vlp.content.entity.Lesson;
import com.vlp.content.repo.CourseRepository;
import com.vlp.content.repo.LessonRepository;
import com.vlp.user.entity.User;
import com.vlp.user.repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthAndLearnIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired LessonRepository lessonRepository;
    @Autowired PasswordEncoder passwordEncoder;

    Long lessonId;
    Long courseId;

    @BeforeEach
    void setUp() {
        // ensure a published course/lesson for progress tests
        if (courseRepository.count() == 0) {
            Course c = courseRepository.save(Course.builder()
                    .title("测试课程")
                    .status("PUBLISHED")
                    .completionRatio(0.5)
                    .summary("test")
                    .build());
            Lesson l = lessonRepository.save(Lesson.builder()
                    .courseId(c.getId())
                    .title("测试课时")
                    .durationSeconds(100)
                    .videoPath("videos/sample.mp4")
                    .sortOrder(1)
                    .build());
            courseId = c.getId();
            lessonId = l.getId();
        } else {
            Course c = courseRepository.findAll().get(0);
            courseId = c.getId();
            lessonId = lessonRepository.findByCourseIdOrderBySortOrderAsc(courseId).get(0).getId();
        }
        if (userRepository.findByUsername("tester").isEmpty()) {
            userRepository.save(User.builder()
                    .username("tester")
                    .passwordHash(passwordEncoder.encode("Test@123"))
                    .nickname("测试员")
                    .email("tester@vlp.local")
                    .role("USER")
                    .enabled(true)
                    .build());
        }
    }

    @Test
    void registerLoginAndReportProgress() throws Exception {
        String uniq = "u" + System.currentTimeMillis();
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"Pass@123","email":"%s@t.com","nickname":"学员"}
                                """.formatted(uniq, uniq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").isNotEmpty());

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"Pass@123"}
                                """.formatted(uniq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        String token = objectMapper.readTree(login.getResponse().getContentAsString()).path("data").path("token").asText();
        assertThat(token).isNotBlank();

        mockMvc.perform(get("/api/public/courses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(post("/api/learn/progress")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lessonId":%d,"positionSeconds":20,"deltaSeconds":10,"heartbeat":true}
                                """.formatted(lessonId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.positionSeconds").value(20));

        // anti-cheat: huge jump should be clamped to max
        MvcResult r2 = mockMvc.perform(post("/api/learn/progress")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lessonId":%d,"positionSeconds":9999,"deltaSeconds":5,"heartbeat":true}
                                """.formatted(lessonId)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(r2.getResponse().getContentAsString()).path("data");
        assertThat(data.path("positionSeconds").asInt()).isLessThan(9999);
    }

    @Test
    void adminRequiresRole() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"tester\",\"password\":\"Test@123\"}"))
                .andExpect(status().isOk());

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"tester\",\"password\":\"Test@123\"}"))
                .andReturn();
        String token = objectMapper.readTree(login.getResponse().getContentAsString()).path("data").path("token").asText();

        mockMvc.perform(get("/api/admin/dashboard").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void healthOk() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
}
