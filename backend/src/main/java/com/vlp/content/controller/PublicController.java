package com.vlp.content.controller;

import com.vlp.common.ApiResponse;
import com.vlp.common.PageResult;
import com.vlp.content.entity.*;
import com.vlp.content.service.ContentService;
import com.vlp.security.AuthContext;
import com.vlp.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicController {
    private final ContentService contentService;

    @GetMapping("/home")
    public ApiResponse<Map<String, Object>> home() {
        UserPrincipal u = AuthContext.optional();
        return ApiResponse.ok(contentService.home(u == null ? null : u.getId()));
    }

    @GetMapping("/news")
    public ApiResponse<PageResult<News>> news(@RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(contentService.newsList(page, size));
    }

    @GetMapping("/news/{id}")
    public ApiResponse<News> newsDetail(@PathVariable Long id) {
        return ApiResponse.ok(contentService.newsDetail(id));
    }

    @GetMapping("/categories")
    public ApiResponse<List<Category>> categories() {
        return ApiResponse.ok(contentService.categories());
    }

    @GetMapping("/courses")
    public ApiResponse<PageResult<Course>> courses(@RequestParam(required = false) Long categoryId,
                                                   @RequestParam(required = false) String keyword,
                                                   @RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "12") int size) {
        return ApiResponse.ok(contentService.courseList(categoryId, keyword, page, size));
    }

    @GetMapping("/courses/{id}")
    public ApiResponse<Map<String, Object>> courseDetail(@PathVariable Long id) {
        UserPrincipal u = AuthContext.optional();
        return ApiResponse.ok(contentService.courseDetail(id, u == null ? null : u.getId()));
    }
}
