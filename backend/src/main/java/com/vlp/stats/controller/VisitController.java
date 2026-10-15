package com.vlp.stats.controller;

import com.vlp.common.ApiResponse;
import com.vlp.security.AuthContext;
import com.vlp.security.UserPrincipal;
import com.vlp.stats.service.VisitService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class VisitController {
    private final VisitService visitService;

    @PostMapping("/api/public/visit")
    public ApiResponse<Void> track(@RequestBody(required = false) Map<String, String> body,
                                   HttpServletRequest request) {
        String path = body == null ? "/" : body.getOrDefault("path", "/");
        String visitorKey = body == null ? null : body.get("visitorKey");
        if (visitorKey == null || visitorKey.isBlank()) {
            visitorKey = OptionalHeader(request);
        }
        UserPrincipal u = AuthContext.optional();
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) ip = request.getRemoteAddr();
        else ip = ip.split(",")[0].trim();
        visitService.track(path, visitorKey, u == null ? null : u.getId(), ip);
        return ApiResponse.ok(null);
    }

    private String OptionalHeader(HttpServletRequest request) {
        String v = request.getHeader("X-Visitor-Key");
        return (v == null || v.isBlank()) ? "anon-" + UUID.randomUUID() : v;
    }
}
