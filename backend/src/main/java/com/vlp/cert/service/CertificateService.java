package com.vlp.cert.service;

import com.vlp.cert.entity.Certificate;
import com.vlp.cert.repo.CertificateRepository;
import com.vlp.content.entity.Course;
import com.vlp.notify.service.NotifyService;
import com.vlp.user.entity.User;
import com.vlp.user.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CertificateService {
    private final CertificateRepository certificateRepository;
    private final UserRepository userRepository;
    private final NotifyService notifyService;

    @Transactional
    public Certificate issueIfAbsent(Long userId, Course course, double rate) {
        return certificateRepository.findByUserIdAndCourseId(userId, course.getId())
                .orElseGet(() -> {
                    User u = userRepository.findById(userId).orElse(null);
                    String no = "VLP" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                            + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
                    Certificate c = Certificate.builder()
                            .userId(userId)
                            .courseId(course.getId())
                            .certNo(no)
                            .courseTitle(course.getTitle())
                            .userName(u == null ? "" : (u.getNickname() != null ? u.getNickname() : u.getUsername()))
                            .completionRate(rate)
                            .build();
                    certificateRepository.save(c);
                    notifyService.send(userId, "结业证书已颁发",
                            "恭喜完成课程《" + course.getTitle() + "》，证书编号：" + no, "CERT");
                    return c;
                });
    }

    public List<Certificate> mine(Long userId) {
        return certificateRepository.findByUserIdOrderByIssuedAtDesc(userId);
    }

    public Certificate byNo(String no) {
        return certificateRepository.findByCertNo(no).orElse(null);
    }

    public List<Certificate> all() {
        return certificateRepository.findAll();
    }
}
