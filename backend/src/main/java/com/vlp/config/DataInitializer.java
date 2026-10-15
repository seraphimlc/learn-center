package com.vlp.config;

import com.vlp.content.entity.*;
import com.vlp.content.repo.*;
import com.vlp.notify.service.NotifyService;
import com.vlp.user.entity.User;
import com.vlp.user.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;
    private final NewsRepository newsRepository;
    private final BannerRepository bannerRepository;
    private final SiteConfigRepository siteConfigRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotifyService notifyService;

    @Value("${vlp.upload.dir}")
    private String uploadDir;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        ensureSampleVideo();
        if (userRepository.findByUsername("admin").isEmpty()) {
            userRepository.save(User.builder()
                    .username("admin")
                    .passwordHash(passwordEncoder.encode("Admin@123"))
                    .nickname("系统管理员")
                    .email("admin@vlp.local")
                    .role("ADMIN")
                    .enabled(true)
                    .build());
            log.info("Seeded admin user");
        }
        if (userRepository.findByUsername("demo").isEmpty()) {
            userRepository.save(User.builder()
                    .username("demo")
                    .passwordHash(passwordEncoder.encode("Demo@123"))
                    .nickname("演示学员")
                    .email("demo@vlp.local")
                    .role("USER")
                    .enabled(true)
                    .build());
        }
        if (categoryRepository.count() == 0) {
            Category c1 = categoryRepository.save(Category.builder().name("职业技能").sortOrder(1).enabled(true).build());
            Category c2 = categoryRepository.save(Category.builder().name("合规培训").sortOrder(2).enabled(true).build());
            Category c3 = categoryRepository.save(Category.builder().name("管理进阶").sortOrder(3).enabled(true).build());

            Course course1 = courseRepository.save(Course.builder()
                    .title("新员工入职指南")
                    .categoryId(c1.getId())
                    .summary("快速了解企业文化、组织架构与日常工作规范，顺利开启职业旅程。")
                    .description("本课程面向新入职员工，涵盖文化认同、制度解读、沟通协作与信息安全基础。")
                    .coverUrl("/api/media/images/placeholder-course.svg")
                    .status("PUBLISHED")
                    .hot(true)
                    .recommended(true)
                    .sortOrder(1)
                    .completionRatio(0.9)
                    .build());
            Lesson l1 = lessonRepository.save(Lesson.builder().courseId(course1.getId()).title("欢迎加入：文化与使命")
                    .videoPath("videos/sample.mp4").durationSeconds(30).sortOrder(1).freePreview(true).build());
            Lesson l2 = lessonRepository.save(Lesson.builder().courseId(course1.getId()).title("组织架构与协作流程")
                    .videoPath("videos/sample.mp4").durationSeconds(30).sortOrder(2).build());
            Lesson l3 = lessonRepository.save(Lesson.builder().courseId(course1.getId()).title("信息安全与合规红线")
                    .videoPath("videos/sample.mp4").durationSeconds(30).sortOrder(3).build());
            course1.setLessonCount(3);
            course1.setDurationSeconds(90);
            courseRepository.save(course1);

            Course course2 = courseRepository.save(Course.builder()
                    .title("数据安全与隐私保护")
                    .categoryId(c2.getId())
                    .summary("掌握数据分级、最小权限与隐私合规要点，降低业务风险。")
                    .description("面向全员的数据安全必修课，结合案例讲解常见风险与防护动作。")
                    .coverUrl("/api/media/images/placeholder-course.svg")
                    .status("PUBLISHED")
                    .hot(true)
                    .recommended(true)
                    .sortOrder(2)
                    .completionRatio(0.9)
                    .build());
            lessonRepository.save(Lesson.builder().courseId(course2.getId()).title("数据分级与访问控制")
                    .videoPath("videos/sample.mp4").durationSeconds(30).sortOrder(1).freePreview(true).build());
            lessonRepository.save(Lesson.builder().courseId(course2.getId()).title("隐私合规实操清单")
                    .videoPath("videos/sample.mp4").durationSeconds(30).sortOrder(2).build());
            course2.setLessonCount(2);
            course2.setDurationSeconds(60);
            courseRepository.save(course2);

            Course course3 = courseRepository.save(Course.builder()
                    .title("高效团队管理实践")
                    .categoryId(c3.getId())
                    .summary("从目标对齐到反馈辅导，提升一线管理者带队能力。")
                    .description("适合新晋管理者与骨干员工，系统学习目标拆解、会议管理与绩效辅导。")
                    .coverUrl("/api/media/images/placeholder-course.svg")
                    .status("PUBLISHED")
                    .recommended(true)
                    .sortOrder(3)
                    .completionRatio(0.9)
                    .build());
            lessonRepository.save(Lesson.builder().courseId(course3.getId()).title("目标对齐与节奏管理")
                    .videoPath("videos/sample.mp4").durationSeconds(30).sortOrder(1).build());
            course3.setLessonCount(1);
            course3.setDurationSeconds(30);
            courseRepository.save(course3);

            log.info("Seeded categories/courses/lessons");
        }
        if (newsRepository.count() == 0) {
            newsRepository.save(News.builder().title("平台上线公告：开启数字化学习新体验")
                    .summary("视频学习平台一期正式上线，支持课程点播、进度跟踪与学情分析。")
                    .content("<p>尊敬的学员：</p><p>视频学习平台一期已正式发布。您可以浏览课程、观看视频并自动同步学习进度。如有问题请联系管理员。</p>")
                    .status("PUBLISHED").publishedAt(LocalDateTime.now()).viewCount(0).build());
            newsRepository.save(News.builder().title("本周学习建议：完成「新员工入职指南」")
                    .summary("建议新同事优先完成本周必修课程，系统将自动记录进度。")
                    .content("<p>请登录后进入课程中心，搜索「新员工入职指南」开始学习。</p>")
                    .status("PUBLISHED").publishedAt(LocalDateTime.now().minusDays(1)).viewCount(0).build());
            newsRepository.save(News.builder().title("学习小贴士：利用笔记与书签复习重点")
                    .summary("播放页支持笔记与书签，方便回顾关键时间点。")
                    .content("<p>在视频播放过程中可随时添加笔记与书签，稍后在「我的学习」中回顾。</p>")
                    .status("PUBLISHED").publishedAt(LocalDateTime.now().minusDays(2)).viewCount(0).build());
        }
        if (bannerRepository.count() == 0) {
            bannerRepository.save(Banner.builder().title("专业成长，从这里开始")
                    .imageUrl("/api/media/images/banner1.svg").linkUrl("/courses").sortOrder(1).enabled(true).build());
            bannerRepository.save(Banner.builder().title("合规培训必修课上线")
                    .imageUrl("/api/media/images/banner2.svg").linkUrl("/courses?categoryId=2").sortOrder(2).enabled(true).build());
        }
        if (siteConfigRepository.count() == 0) {
            siteConfigRepository.save(SiteConfig.builder().configKey("site.name").configValue("知行学堂").remark("站点名称").build());
            siteConfigRepository.save(SiteConfig.builder().configKey("learn.alert.inactiveDays").configValue("7").remark("未学习预警天数").build());
            siteConfigRepository.save(SiteConfig.builder().configKey("learn.completionRatio").configValue("0.9").remark("默认结业比例").build());
            siteConfigRepository.save(SiteConfig.builder().configKey("feature.aiAnalysis").configValue("true").remark("AI学情分析开关").build());
        }
        if (siteConfigRepository.findById("seed.welcomeSent").isEmpty()) {
            notifyService.announce("欢迎使用知行学堂", "平台已就绪：浏览课程、记录进度、获取结业证书。祝学习顺利！");
            siteConfigRepository.save(SiteConfig.builder().configKey("seed.welcomeSent").configValue("true").remark("欢迎公告已发送").build());
        }
    }

    private void ensureSampleVideo() throws Exception {
        Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(root.resolve("videos"));
        Files.createDirectories(root.resolve("images"));
        Path sample = root.resolve("videos/sample.mp4");
        if (!Files.exists(sample)) {
            // Generate a tiny valid-ish mp4 via ffmpeg if available, else placeholder bytes + note
            ProcessBuilder pb = new ProcessBuilder("ffmpeg", "-y", "-f", "lavfi", "-i", "color=c=0x0F766E:s=1280x720:d=30",
                    "-f", "lavfi", "-i", "sine=f=440:d=30", "-c:v", "libx264", "-t", "30", "-pix_fmt", "yuv420p",
                    "-c:a", "aac", sample.toString());
            pb.redirectErrorStream(true);
            try {
                Process p = pb.start();
                p.getInputStream().readAllBytes();
                int code = p.waitFor();
                if (code != 0 || !Files.exists(sample)) {
                    Files.writeString(sample, "placeholder");
                    log.warn("ffmpeg unavailable; sample.mp4 is placeholder");
                } else {
                    log.info("Generated sample.mp4");
                }
            } catch (Exception e) {
                Files.writeString(sample, "placeholder");
                log.warn("Could not generate sample video: {}", e.getMessage());
            }
        }
        writeSvg(root.resolve("images/placeholder-course.svg"), "#0F766E", "课程封面");
        writeSvg(root.resolve("images/banner1.svg"), "#134E4A", "专业成长，从这里开始");
        writeSvg(root.resolve("images/banner2.svg"), "#1E3A5F", "合规培训必修课上线");
    }

    private void writeSvg(Path path, String color, String text) throws Exception {
        if (Files.exists(path)) return;
        String svg = """
                <svg xmlns="http://www.w3.org/2000/svg" width="1280" height="720" viewBox="0 0 1280 720">
                  <defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1">
                    <stop offset="0%%" stop-color="%s"/><stop offset="100%%" stop-color="#0B1220"/>
                  </linearGradient></defs>
                  <rect width="1280" height="720" fill="url(#g)"/>
                  <text x="640" y="360" fill="white" font-size="48" font-family="system-ui,sans-serif"
                        text-anchor="middle" dominant-baseline="middle">%s</text>
                </svg>
                """.formatted(color, text);
        Files.writeString(path, svg);
    }
}
