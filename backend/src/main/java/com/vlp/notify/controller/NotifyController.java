package com.vlp.notify.controller;

import com.vlp.common.ApiResponse;
import com.vlp.common.PageResult;
import com.vlp.notify.entity.Notification;
import com.vlp.notify.service.NotifyService;
import com.vlp.security.AuthContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotifyController {
    private final NotifyService notifyService;

    @GetMapping
    public ApiResponse<PageResult<Notification>> list(@RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(notifyService.list(AuthContext.current().getId(), page, size));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Long>> unread() {
        return ApiResponse.ok(Map.of("count", notifyService.unread(AuthContext.current().getId())));
    }

    @PostMapping("/{id}/read")
    public ApiResponse<Void> read(@PathVariable Long id) {
        notifyService.markRead(AuthContext.current().getId(), id);
        return ApiResponse.ok(null);
    }
}
