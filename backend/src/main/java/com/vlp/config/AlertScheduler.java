package com.vlp.config;

import com.vlp.content.repo.SiteConfigRepository;
import com.vlp.learn.entity.StudyAlert;
import com.vlp.learn.entity.StudyProgress;
import com.vlp.learn.repo.StudyAlertRepository;
import com.vlp.learn.repo.StudyProgressRepository;
import com.vlp.notify.service.NotifyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlertScheduler {
    private final StudyProgressRepository progressRepository;
    private final StudyAlertRepository alertRepository;
    private final SiteConfigRepository siteConfigRepository;
    private final NotifyService notifyService;

    @Scheduled(cron = "0 0 9 * * *")
    public void scanInactive() {
        int days = 7;
        try {
            days = Integer.parseInt(siteConfigRepository.findById("learn.alert.inactiveDays")
                    .map(c -> c.getConfigValue()).orElse("7"));
        } catch (Exception ignored) {}
        LocalDateTime before = LocalDateTime.now().minusDays(days);
        List<StudyProgress> inactive = progressRepository.findInactiveSince(before);
        Set<Long> notified = new HashSet<>();
        for (StudyProgress p : inactive) {
            if (!notified.add(p.getUserId())) continue;
            if (Boolean.TRUE.equals(p.getCompleted())) continue;
            String msg = "您已超过 " + days + " 天未继续学习，建议尽快完成课程进度。";
            StudyAlert alert = alertRepository.save(StudyAlert.builder()
                    .userId(p.getUserId())
                    .courseId(p.getCourseId())
                    .type("INACTIVE")
                    .message(msg)
                    .notified(true)
                    .build());
            notifyService.send(p.getUserId(), "学情预警：学习中断", msg, "ALERT");
            log.info("Inactive alert {} for user {}", alert.getId(), p.getUserId());
        }
    }
}
