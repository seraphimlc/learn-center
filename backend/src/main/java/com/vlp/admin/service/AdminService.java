package com.vlp.admin.service;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.vlp.ai.service.AiService;
import com.vlp.stats.service.VisitService;
import com.vlp.cert.entity.Certificate;
import com.vlp.cert.service.CertificateService;
import com.vlp.common.BusinessException;
import com.vlp.common.HtmlSanitizer;
import com.vlp.common.PageResult;
import com.vlp.content.entity.*;
import com.vlp.content.repo.*;
import com.vlp.learn.entity.StudyProgress;
import com.vlp.learn.repo.StudyProgressRepository;
import com.vlp.notify.service.NotifyService;
import com.vlp.security.AdminRoles;
import com.vlp.security.UserPrincipal;
import com.vlp.upload.UploadService;
import com.vlp.user.entity.User;
import com.vlp.user.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 管理端聚合服务。所有写路径必须经 {@link AdminRoles} 细粒度校验；
 * Spring Security 的 hasAnyRole 只负责「能否进入管理 API」。
 */
@Service
@RequiredArgsConstructor
public class AdminService {
    private final UserRepository userRepository;
    private final NewsRepository newsRepository;
    private final CategoryRepository categoryRepository;
    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;
    private final BannerRepository bannerRepository;
    private final SiteConfigRepository siteConfigRepository;
    private final StudyProgressRepository progressRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final UploadService uploadService;
    private final NotifyService notifyService;
    private final CertificateService certificateService;
    private final AiService aiService;
    private final VisitService visitService;

    public Map<String, Object> visitStats(int days) {
        return visitService.stats(days);
    }

    public Map<String, Object> dashboard() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userCount", userRepository.count());
        m.put("enabledUsers", userRepository.countByEnabledTrue());
        m.put("courseCount", courseRepository.count());
        m.put("publishedCourses", courseRepository.countByStatus("PUBLISHED"));
        m.put("learnerCount", progressRepository.countDistinctLearners());
        m.put("totalStudiedSeconds", Optional.ofNullable(progressRepository.sumStudiedSeconds()).orElse(0L));
        m.put("completedLessons", progressRepository.countByCompletedTrue());
        m.put("newsCount", newsRepository.count());
        m.put("certCount", certificateService.all().size());
        try {
            Map<String, Object> visit = visitService.stats(14);
            m.put("todayPv", visit.get("todayPv"));
            m.put("todayUv", visit.get("todayUv"));
            m.put("visitTrend", visit.get("trend"));
        } catch (Exception ignored) {
            m.put("todayPv", 0);
            m.put("todayUv", 0);
            m.put("visitTrend", List.of());
        }
        return m;
    }

    public PageResult<User> users(String kw, String role, int page, int size) {
        return PageResult.of(userRepository.search(kw, role, PageRequest.of(page - 1, size)));
    }

    @Transactional
    public User setEnabled(UserPrincipal op, Long id, boolean enabled) {
        AdminRoles.requireUserManager(op);
        User u = userRepository.findById(id).orElseThrow(() -> new BusinessException("用户不存在"));
        if ("ADMIN".equals(u.getRole()) && !enabled) throw new BusinessException("不能禁用管理员");
        u.setEnabled(enabled);
        userRepository.save(u);
        auditService.log(op, enabled ? "ENABLE_USER" : "DISABLE_USER", "USER", String.valueOf(id), u.getUsername());
        return u;
    }

    @Transactional
    public void resetPassword(UserPrincipal op, Long id, String newPassword) {
        AdminRoles.requireUserManager(op);
        User u = userRepository.findById(id).orElseThrow(() -> new BusinessException("用户不存在"));
        u.setPasswordHash(passwordEncoder.encode(newPassword == null || newPassword.isBlank() ? "Reset@123" : newPassword));
        userRepository.save(u);
        auditService.log(op, "RESET_PASSWORD", "USER", String.valueOf(id), u.getUsername());
    }


    @Transactional
    public User setRole(UserPrincipal op, Long id, String role) {
        AdminRoles.requireUserManager(op);
        User u = userRepository.findById(id).orElseThrow(() -> new BusinessException("用户不存在"));
        String r = role == null ? "USER" : role.trim().toUpperCase();
        if (!List.of("USER", "ADMIN", "CONTENT_ADMIN", "DATA_VIEWER").contains(r)) {
            throw new BusinessException("不支持的角色");
        }
        if ("ADMIN".equals(u.getRole()) && !"ADMIN".equals(r) && userRepository.countByRole("ADMIN") <= 1) {
            throw new BusinessException("不能取消唯一超级管理员");
        }
        u.setRole(r);
        userRepository.save(u);
        auditService.log(op, "SET_ROLE", "USER", String.valueOf(id), r);
        return u;
    }

    // ---- News ----
    public PageResult<News> newsAdmin(String kw, int page, int size) {
        if (kw != null && !kw.isBlank()) {
            return PageResult.of(newsRepository.findByTitleContainingOrderByCreatedAtDesc(kw, PageRequest.of(page - 1, size)));
        }
        return PageResult.of(newsRepository.findAll(PageRequest.of(page - 1, size)));
    }

    @Transactional
    public News saveNews(UserPrincipal op, News news) {
        AdminRoles.requireContentWriter(op);
        if (news.getId() == null) news.setCreatedBy(op.getId());
        if ("PUBLISHED".equals(news.getStatus()) && news.getPublishedAt() == null) {
            news.setPublishedAt(LocalDateTime.now());
        }
        // 用户端 NewsDetail 使用 v-html，入库前收敛 script/事件属性
        news.setContent(HtmlSanitizer.sanitize(news.getContent()));
        News saved = newsRepository.save(news);
        auditService.log(op, "SAVE_NEWS", "NEWS", String.valueOf(saved.getId()), saved.getTitle());
        return saved;
    }

    @Transactional
    public void deleteNews(UserPrincipal op, Long id) {
        AdminRoles.requireContentWriter(op);
        newsRepository.deleteById(id);
        auditService.log(op, "DELETE_NEWS", "NEWS", String.valueOf(id), null);
    }

    // ---- Category ----
    public List<Category> allCategories() { return categoryRepository.findAllByOrderBySortOrderAsc(); }

    @Transactional
    public Category saveCategory(UserPrincipal op, Category c) {
        AdminRoles.requireContentWriter(op);
        Category saved = categoryRepository.save(c);
        auditService.log(op, "SAVE_CATEGORY", "CATEGORY", String.valueOf(saved.getId()), saved.getName());
        return saved;
    }

    @Transactional
    public void deleteCategory(UserPrincipal op, Long id) {
        AdminRoles.requireContentWriter(op);
        categoryRepository.deleteById(id);
        auditService.log(op, "DELETE_CATEGORY", "CATEGORY", String.valueOf(id), null);
    }

    // ---- Course / Lesson ----
    public PageResult<Course> courses(String status, Long catId, String kw, int page, int size) {
        return PageResult.of(courseRepository.adminSearch(status, catId, kw, PageRequest.of(page - 1, size)));
    }

    @Transactional
    public Course saveCourse(UserPrincipal op, Course c) {
        AdminRoles.requireContentWriter(op);
        if (c.getId() == null) c.setCreatedBy(op.getId());
        c.setDescription(HtmlSanitizer.sanitize(c.getDescription()));
        Course saved = courseRepository.save(c);
        auditService.log(op, "SAVE_COURSE", "COURSE", String.valueOf(saved.getId()), saved.getTitle());
        return saved;
    }

    @Transactional
    public void deleteCourse(UserPrincipal op, Long id) {
        AdminRoles.requireContentWriter(op);
        lessonRepository.deleteByCourseId(id);
        courseRepository.deleteById(id);
        auditService.log(op, "DELETE_COURSE", "COURSE", String.valueOf(id), null);
    }

    public List<Lesson> lessons(Long courseId) {
        return lessonRepository.findByCourseIdOrderBySortOrderAsc(courseId);
    }

    @Transactional
    public Lesson saveLesson(UserPrincipal op, Lesson lesson) {
        AdminRoles.requireContentWriter(op);
        Lesson saved = lessonRepository.save(lesson);
        refreshCourseMeta(lesson.getCourseId());
        auditService.log(op, "SAVE_LESSON", "LESSON", String.valueOf(saved.getId()), saved.getTitle());
        return saved;
    }

    @Transactional
    public void deleteLesson(UserPrincipal op, Long id) {
        AdminRoles.requireContentWriter(op);
        Lesson l = lessonRepository.findById(id).orElseThrow(() -> new BusinessException("课时不存在"));
        Long cid = l.getCourseId();
        lessonRepository.delete(l);
        refreshCourseMeta(cid);
        auditService.log(op, "DELETE_LESSON", "LESSON", String.valueOf(id), null);
    }

    private void refreshCourseMeta(Long courseId) {
        List<Lesson> list = lessonRepository.findByCourseIdOrderBySortOrderAsc(courseId);
        courseRepository.findById(courseId).ifPresent(c -> {
            c.setLessonCount(list.size());
            c.setDurationSeconds(list.stream().mapToInt(x -> Optional.ofNullable(x.getDurationSeconds()).orElse(0)).sum());
            courseRepository.save(c);
        });
    }

    public Map<String, String> upload(UserPrincipal op, MultipartFile file, String type) {
        AdminRoles.requireContentWriter(op);
        String path = "image".equalsIgnoreCase(type) ? uploadService.storeImage(file) : uploadService.storeVideo(file);
        auditService.log(op, "UPLOAD", type.toUpperCase(), path, file.getOriginalFilename());
        Map<String, String> m = new LinkedHashMap<>();
        m.put("path", path);
        m.put("url", "/api/media/" + path);
        return m;
    }

    // ---- Banner / Config / Announce ----
    public List<Banner> banners() { return bannerRepository.findAllByOrderBySortOrderAsc(); }

    @Transactional
    public Banner saveBanner(UserPrincipal op, Banner b) {
        AdminRoles.requireContentWriter(op);
        Banner saved = bannerRepository.save(b);
        auditService.log(op, "SAVE_BANNER", "BANNER", String.valueOf(saved.getId()), saved.getTitle());
        return saved;
    }

    @Transactional
    public void deleteBanner(UserPrincipal op, Long id) {
        AdminRoles.requireContentWriter(op);
        bannerRepository.deleteById(id);
        auditService.log(op, "DELETE_BANNER", "BANNER", String.valueOf(id), null);
    }

    public List<SiteConfig> configs() { return siteConfigRepository.findAll(); }

    @Transactional
    public SiteConfig saveConfig(UserPrincipal op, SiteConfig c) {
        AdminRoles.requireConfigWriter(op);
        SiteConfig saved = siteConfigRepository.save(c);
        auditService.log(op, "SAVE_CONFIG", "CONFIG", c.getConfigKey(), c.getConfigValue());
        return saved;
    }

    @Transactional
    public void announce(UserPrincipal op, String title, String content) {
        AdminRoles.requireContentWriter(op);
        notifyService.announce(title, content);
        auditService.log(op, "ANNOUNCE", "NOTIFY", null, title);
    }

    public Map<String, Object> aiCourse(UserPrincipal op, Long courseId, boolean apply) {
        AdminRoles.requireContentWriter(op);
        Map<String, Object> r = aiService.courseAssist(courseId, apply);
        auditService.log(op, "AI_COURSE", "COURSE", String.valueOf(courseId), "apply=" + apply);
        return r;
    }

    public Map<String, Object> aiNews(UserPrincipal op, Long newsId, boolean apply) {
        AdminRoles.requireContentWriter(op);
        Map<String, Object> r = aiService.newsAssist(newsId, apply);
        auditService.log(op, "AI_NEWS", "NEWS", String.valueOf(newsId), "apply=" + apply);
        return r;
    }

    public byte[] exportStudyCsv(UserPrincipal op) throws Exception {
        AdminRoles.requireDataExporter(op);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (CSVWriter writer = new CSVWriter(new OutputStreamWriter(bos, StandardCharsets.UTF_8))) {
            writer.writeNext(new String[]{"userId", "courseId", "lessonId", "positionSeconds", "studiedSeconds", "completed", "updatedAt"});
            for (StudyProgress p : progressRepository.findAll()) {
                writer.writeNext(new String[]{
                        String.valueOf(p.getUserId()), String.valueOf(p.getCourseId()), String.valueOf(p.getLessonId()),
                        String.valueOf(p.getPositionSeconds()), String.valueOf(p.getStudiedSeconds()),
                        String.valueOf(p.getCompleted()), String.valueOf(p.getUpdatedAt())
                });
            }
        }
        return bos.toByteArray();
    }

    public byte[] userImportTemplate(UserPrincipal op) throws Exception {
        AdminRoles.requireUserManager(op);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (CSVWriter writer = new CSVWriter(new OutputStreamWriter(bos, StandardCharsets.UTF_8))) {
            writer.writeNext(new String[]{"username", "password", "email", "nickname"});
            writer.writeNext(new String[]{"demo01", "Demo@123", "demo01@example.com", "演示学员01"});
        }
        return bos.toByteArray();
    }

    @Transactional
    public Map<String, Object> importUsers(UserPrincipal op, MultipartFile file) throws Exception {
        AdminRoles.requireUserManager(op);
        int ok = 0, skip = 0;
        try (CSVReader reader = new CSVReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            List<String[]> rows = reader.readAll();
            for (int i = 1; i < rows.size(); i++) {
                String[] r = rows.get(i);
                if (r.length < 2 || r[0] == null || r[0].isBlank()) { skip++; continue; }
                if (userRepository.existsByUsername(r[0].trim())) { skip++; continue; }
                String pwd = r.length > 1 && r[1] != null && !r[1].isBlank() ? r[1] : "Reset@123";
                userRepository.save(User.builder()
                        .username(r[0].trim())
                        .passwordHash(passwordEncoder.encode(pwd))
                        .email(r.length > 2 ? r[2] : null)
                        .nickname(r.length > 3 && r[3] != null && !r[3].isBlank() ? r[3] : r[0].trim())
                        .role("USER")
                        .enabled(true)
                        .build());
                ok++;
            }
        }
        auditService.log(op, "IMPORT_USERS", "USER", null, "ok=" + ok + ",skip=" + skip);
        return Map.of("imported", ok, "skipped", skip);
    }

    public List<Certificate> certificates() { return certificateService.all(); }
}
