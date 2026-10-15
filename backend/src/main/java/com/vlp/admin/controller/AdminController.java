package com.vlp.admin.controller;

import com.vlp.admin.entity.AuditLog;
import com.vlp.admin.service.AdminService;
import com.vlp.admin.service.AuditService;
import com.vlp.cert.entity.Certificate;
import com.vlp.common.ApiResponse;
import com.vlp.common.PageResult;
import com.vlp.content.entity.*;
import com.vlp.security.AuthContext;
import com.vlp.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {
    private final AdminService adminService;
    private final AuditService auditService;

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> dashboard() {
        return ApiResponse.ok(adminService.dashboard());
    }

    @GetMapping("/visit-stats")
    public ApiResponse<Map<String, Object>> visitStats(@RequestParam(defaultValue = "14") int days) {
        return ApiResponse.ok(adminService.visitStats(days));
    }

    @GetMapping("/users")
    public ApiResponse<PageResult<User>> users(@RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) String role,
                                               @RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(adminService.users(keyword, role, page, size));
    }

    @PostMapping("/users/{id}/enable")
    public ApiResponse<User> enable(@PathVariable Long id, @RequestParam boolean enabled) {
        return ApiResponse.ok(adminService.setEnabled(AuthContext.current(), id, enabled));
    }

    @PostMapping("/users/{id}/role")
    public ApiResponse<User> setRole(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ApiResponse.ok(adminService.setRole(AuthContext.current(), id, body.get("role")));
    }

    @PostMapping("/users/{id}/reset-password")
    public ApiResponse<Void> resetPwd(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        String pwd = body == null ? null : body.get("password");
        adminService.resetPassword(AuthContext.current(), id, pwd);
        return ApiResponse.ok("密码已重置", null);
    }

    @GetMapping("/news")
    public ApiResponse<PageResult<News>> news(@RequestParam(required = false) String keyword,
                                              @RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(adminService.newsAdmin(keyword, page, size));
    }

    @PostMapping("/news")
    public ApiResponse<News> saveNews(@RequestBody News news) {
        return ApiResponse.ok(adminService.saveNews(AuthContext.current(), news));
    }

    @DeleteMapping("/news/{id}")
    public ApiResponse<Void> delNews(@PathVariable Long id) {
        adminService.deleteNews(AuthContext.current(), id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/categories")
    public ApiResponse<List<Category>> categories() {
        return ApiResponse.ok(adminService.allCategories());
    }

    @PostMapping("/categories")
    public ApiResponse<Category> saveCat(@RequestBody Category c) {
        return ApiResponse.ok(adminService.saveCategory(AuthContext.current(), c));
    }

    @DeleteMapping("/categories/{id}")
    public ApiResponse<Void> delCat(@PathVariable Long id) {
        adminService.deleteCategory(AuthContext.current(), id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/courses")
    public ApiResponse<PageResult<Course>> courses(@RequestParam(required = false) String status,
                                                   @RequestParam(required = false) Long categoryId,
                                                   @RequestParam(required = false) String keyword,
                                                   @RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(adminService.courses(status, categoryId, keyword, page, size));
    }

    @PostMapping("/courses")
    public ApiResponse<Course> saveCourse(@RequestBody Course c) {
        return ApiResponse.ok(adminService.saveCourse(AuthContext.current(), c));
    }

    @DeleteMapping("/courses/{id}")
    public ApiResponse<Void> delCourse(@PathVariable Long id) {
        adminService.deleteCourse(AuthContext.current(), id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/courses/{id}/lessons")
    public ApiResponse<List<Lesson>> lessons(@PathVariable Long id) {
        return ApiResponse.ok(adminService.lessons(id));
    }

    @PostMapping("/lessons")
    public ApiResponse<Lesson> saveLesson(@RequestBody Lesson lesson) {
        return ApiResponse.ok(adminService.saveLesson(AuthContext.current(), lesson));
    }

    @DeleteMapping("/lessons/{id}")
    public ApiResponse<Void> delLesson(@PathVariable Long id) {
        adminService.deleteLesson(AuthContext.current(), id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/upload")
    public ApiResponse<Map<String, String>> upload(@RequestParam("file") MultipartFile file,
                                                   @RequestParam(defaultValue = "video") String type) {
        return ApiResponse.ok(adminService.upload(AuthContext.current(), file, type));
    }

    @GetMapping("/banners")
    public ApiResponse<List<Banner>> banners() {
        return ApiResponse.ok(adminService.banners());
    }

    @PostMapping("/banners")
    public ApiResponse<Banner> saveBanner(@RequestBody Banner b) {
        return ApiResponse.ok(adminService.saveBanner(AuthContext.current(), b));
    }

    @DeleteMapping("/banners/{id}")
    public ApiResponse<Void> delBanner(@PathVariable Long id) {
        adminService.deleteBanner(AuthContext.current(), id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/configs")
    public ApiResponse<List<SiteConfig>> configs() {
        return ApiResponse.ok(adminService.configs());
    }

    @PostMapping("/configs")
    public ApiResponse<SiteConfig> saveConfig(@RequestBody SiteConfig c) {
        return ApiResponse.ok(adminService.saveConfig(AuthContext.current(), c));
    }

    @PostMapping("/announce")
    public ApiResponse<Void> announce(@RequestBody Map<String, String> body) {
        adminService.announce(AuthContext.current(), body.get("title"), body.get("content"));
        return ApiResponse.ok("公告已发送", null);
    }

    @GetMapping("/audit-logs")
    public ApiResponse<PageResult<AuditLog>> audits(@RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        Page<AuditLog> p = auditService.page(page, size);
        return ApiResponse.ok(PageResult.of(p));
    }

    @PostMapping("/ai/news/{id}")
    public ApiResponse<Map<String, Object>> aiNews(@PathVariable Long id,
                                                   @RequestParam(defaultValue = "false") boolean apply) {
        return ApiResponse.ok(adminService.aiNews(AuthContext.current(), id, apply));
    }

    @PostMapping("/ai/course/{id}")
    public ApiResponse<Map<String, Object>> aiCourse(@PathVariable Long id,
                                                     @RequestParam(defaultValue = "false") boolean apply) {
        return ApiResponse.ok(adminService.aiCourse(AuthContext.current(), id, apply));
    }

    @GetMapping("/export/study")
    public ResponseEntity<byte[]> exportStudy() throws Exception {
        byte[] data = adminService.exportStudyCsv(AuthContext.current());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=study-export.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(data);
    }

    @GetMapping("/users/import-template")
    public ResponseEntity<byte[]> importTemplate() throws Exception {
        // 模板含示例口令列，仅超管
        byte[] body = adminService.userImportTemplate(AuthContext.current());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=user-import-template.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(body);
    }

    @PostMapping("/users/import")
    public ApiResponse<Map<String, Object>> importUsers(@RequestParam("file") MultipartFile file) throws Exception {
        return ApiResponse.ok(adminService.importUsers(AuthContext.current(), file));
    }

    @GetMapping("/certificates")
    public ApiResponse<List<Certificate>> certs() {
        return ApiResponse.ok(adminService.certificates());
    }
}
