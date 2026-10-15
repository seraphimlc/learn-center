package com.vlp.stats.service;

import com.vlp.stats.entity.VisitLog;
import com.vlp.stats.repo.VisitLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class VisitService {
    private final VisitLogRepository visitLogRepository;

    @Transactional
    public void track(String path, String visitorKey, Long userId, String ip) {
        if (path == null || path.isBlank()) path = "/";
        if (path.length() > 250) path = path.substring(0, 250);
        visitLogRepository.save(VisitLog.builder()
                .path(path)
                .visitorKey(visitorKey == null || visitorKey.isBlank() ? "anon" : visitorKey)
                .userId(userId)
                .ip(ip == null ? "" : (ip.length() > 64 ? ip.substring(0, 64) : ip))
                .build());
    }

    public Map<String, Object> stats(int days) {
        int d = Math.max(1, Math.min(days, 90));
        LocalDateTime after = LocalDate.now().minusDays(d - 1L).atStartOfDay();
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("todayPv", visitLogRepository.countByCreatedAtAfter(todayStart));
        m.put("todayUv", visitLogRepository.countDistinctVisitorsSince(todayStart));
        m.put("rangePv", visitLogRepository.countByCreatedAtAfter(after));
        m.put("rangeUv", visitLogRepository.countDistinctVisitorsSince(after));
        m.put("days", d);
        List<Map<String, Object>> trend = new ArrayList<>();
        Map<String, long[]> byDay = new LinkedHashMap<>();
        for (int i = d - 1; i >= 0; i--) {
            String key = LocalDate.now().minusDays(i).toString();
            byDay.put(key, new long[]{0, 0});
        }
        for (Object[] row : visitLogRepository.dailyStats(after)) {
            String key = String.valueOf(row[0]);
            long pv = ((Number) row[1]).longValue();
            long uv = ((Number) row[2]).longValue();
            byDay.put(key, new long[]{pv, uv});
        }
        byDay.forEach((k, v) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", k);
            item.put("pv", v[0]);
            item.put("uv", v[1]);
            trend.add(item);
        });
        m.put("trend", trend);
        return m;
    }
}
