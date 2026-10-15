package com.vlp.notify.repo;

import com.vlp.notify.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    @Query("SELECT n FROM Notification n WHERE n.userId = :uid OR n.userId IS NULL ORDER BY n.createdAt DESC")
    Page<Notification> findForUser(@Param("uid") Long uid, Pageable pageable);

    @Query("SELECT COUNT(n) FROM Notification n WHERE (n.userId = :uid OR n.userId IS NULL) AND n.readFlag = false")
    long countUnread(@Param("uid") Long uid);
}
