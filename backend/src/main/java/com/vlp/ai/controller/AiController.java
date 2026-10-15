package com.vlp.ai.controller;

import com.vlp.ai.service.AiService;
import com.vlp.common.ApiResponse;
import com.vlp.security.AuthContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AI 学情中心 API（需登录）。LLM 不可用时 AiService 返回 degraded 规则兜底，不抛 500。
 * 知识点/摘要等可落 ai_cache；测验会话暂存在内存（单机）。
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {
    private final AiService aiService;

    @GetMapping("/analysis")
    public ApiResponse<Map<String, Object>> analysis() {
        return ApiResponse.ok(aiService.learningAnalysis(AuthContext.current().getId()));
    }

    @GetMapping("/recommend")
    public ApiResponse<List<Map<String, Object>>> recommend() {
        return ApiResponse.ok(aiService.recommendWithReason(AuthContext.current().getId()));
    }

    @PostMapping("/knowledge-points")
    public ApiResponse<Map<String, Object>> knowledge(@RequestBody Map<String, Object> body) {
        Long courseId = toLong(body.get("courseId"));
        Long lessonId = toLong(body.get("lessonId"));
        if (courseId == null) throw new com.vlp.common.BusinessException("courseId 必填");
        return ApiResponse.ok(aiService.knowledgePoints(courseId, lessonId));
    }

    @PostMapping("/quiz")
    public ApiResponse<Map<String, Object>> quiz(@RequestBody Map<String, Object> body) {
        Long courseId = toLong(body.get("courseId"));
        Long lessonId = toLong(body.get("lessonId"));
        Integer count = body.get("count") == null ? null : Integer.parseInt(String.valueOf(body.get("count")));
        if (courseId == null) throw new com.vlp.common.BusinessException("courseId 必填");
        return ApiResponse.ok(aiService.generateQuiz(AuthContext.current().getId(), courseId, lessonId, count));
    }

    @PostMapping("/quiz/grade")
    @SuppressWarnings("unchecked")
    public ApiResponse<Map<String, Object>> grade(@RequestBody Map<String, Object> body) {
        Long courseId = toLong(body.get("courseId"));
        if (courseId == null) throw new com.vlp.common.BusinessException("courseId 必填");
        List<Map<String, Object>> answers = (List<Map<String, Object>>) body.get("answers");
        return ApiResponse.ok(aiService.gradeQuiz(AuthContext.current().getId(), courseId, answers));
    }

    @PostMapping("/ask")
    public ApiResponse<Map<String, Object>> ask(@RequestBody Map<String, Object> body) {
        Long courseId = toLong(body.get("courseId"));
        Long lessonId = toLong(body.get("lessonId"));
        String question = body.get("question") == null ? null : String.valueOf(body.get("question"));
        if (courseId == null) throw new com.vlp.common.BusinessException("courseId 必填");
        return ApiResponse.ok(aiService.ask(courseId, lessonId, question));
    }

    @PostMapping("/lesson-digest")
    public ApiResponse<Map<String, Object>> digest(@RequestBody Map<String, Object> body) {
        Long lessonId = toLong(body.get("lessonId"));
        if (lessonId == null) throw new com.vlp.common.BusinessException("lessonId 必填");
        return ApiResponse.ok(aiService.lessonDigest(lessonId));
    }

    private Long toLong(Object v) {
        if (v == null || String.valueOf(v).isBlank()) return null;
        return Long.parseLong(String.valueOf(v));
    }
}
