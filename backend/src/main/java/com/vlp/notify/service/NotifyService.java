package com.vlp.notify.service;

import com.vlp.common.PageResult;
import com.vlp.notify.entity.Notification;
import com.vlp.notify.repo.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotifyService {
    private final NotificationRepository notificationRepository;

    @Transactional
    public Notification send(Long userId, String title, String content, String type) {
        return notificationRepository.save(Notification.builder()
                .userId(userId)
                .title(title)
                .content(content)
                .type(type)
                .readFlag(false)
                .build());
    }

    @Transactional
    public Notification announce(String title, String content) {
        return notificationRepository.save(Notification.builder()
                .userId(null)
                .title(title)
                .content(content)
                .type("ANNOUNCEMENT")
                .readFlag(false)
                .build());
    }

    public PageResult<Notification> list(Long userId, int page, int size) {
        return PageResult.of(notificationRepository.findForUser(userId, PageRequest.of(page - 1, size)));
    }

    public long unread(Long userId) {
        return notificationRepository.countUnread(userId);
    }

    @Transactional
    public void markRead(Long userId, Long id) {
        notificationRepository.findById(id).ifPresent(n -> {
            if (n.getUserId() == null || n.getUserId().equals(userId)) {
                n.setReadFlag(true);
                notificationRepository.save(n);
            }
        });
    }
}
