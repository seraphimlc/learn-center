package com.vlp.admin.service;

import com.vlp.admin.entity.AuditLog;
import com.vlp.admin.repo.AuditLogRepository;
import com.vlp.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
@RequiredArgsConstructor
public class AuditService {
    private final AuditLogRepository auditLogRepository;

    public void log(UserPrincipal op, String action, String resourceType, String resourceId, String detail) {
        String ip = null;
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest req = attrs.getRequest();
                ip = req.getHeader("X-Forwarded-For");
                if (ip == null) ip = req.getRemoteAddr();
            }
        } catch (Exception ignored) {}
        auditLogRepository.save(AuditLog.builder()
                .operatorId(op == null ? null : op.getId())
                .operatorName(op == null ? null : op.getUsername())
                .action(action)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .detail(detail)
                .ip(ip)
                .build());
    }

    public Page<AuditLog> page(int page, int size) {
        return auditLogRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page - 1, size));
    }
}
