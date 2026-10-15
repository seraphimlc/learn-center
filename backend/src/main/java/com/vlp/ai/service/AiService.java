package com.vlp.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vlp.ai.entity.AiCache;
import com.vlp.ai.repo.AiCacheRepository;
import com.vlp.common.BusinessException;
import com.vlp.content.entity.Course;
import com.vlp.content.entity.Lesson;
import com.vlp.content.entity.News;
import com.vlp.content.repo.CourseRepository;
import com.vlp.content.repo.LessonRepository;
import com.vlp.content.repo.NewsRepository;
import com.vlp.learn.entity.StudyProgress;
import com.vlp.learn.repo.StudyProgressRepository;
import com.vlp.user.entity.User;
import com.vlp.user.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {
    private final LlmClient llmClient;
    private final StudyProgressRepository progressRepository;
    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;
    private final UserRepository userRepository;
    private final AiCacheRepository aiCacheRepository;
    private final NewsRepository newsRepository;
    private final ObjectMapper objectMapper;

    /** ephemeral last quiz per user for grading without DB */
    private final ConcurrentHashMap<String, List<Map<String, Object>>> quizSession = new ConcurrentHashMap<>();

    public Map<String, Object> learningAnalysis(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException("用户不存在"));
        List<StudyProgress> progresses = progressRepository.findByUserIdOrderByUpdatedAtDesc(userId);
        Map<Long, List<StudyProgress>> byCourse = progresses.stream().collect(Collectors.groupingBy(StudyProgress::getCourseId));

        StringBuilder sb = new StringBuilder();
        sb.append("学员：").append(user.getNickname()).append("\n");
        List<String> courseLines = new ArrayList<>();
        int totalDone = 0;
        int totalLessons = 0;
        for (Map.Entry<Long, List<StudyProgress>> e : byCourse.entrySet()) {
            Course c = courseRepository.findById(e.getKey()).orElse(null);
            if (c == null) continue;
            List<Lesson> lessons = lessonRepository.findByCourseIdOrderBySortOrderAsc(c.getId());
            long done = e.getValue().stream().filter(p -> Boolean.TRUE.equals(p.getCompleted())).count();
            int studied = e.getValue().stream().mapToInt(p -> Optional.ofNullable(p.getStudiedSeconds()).orElse(0)).sum();
            totalDone += (int) done;
            totalLessons += lessons.size();
            String line = "课程《" + c.getTitle() + "》完成 " + done + "/" + lessons.size()
                    + " 课时，累计学习 " + (studied / 60) + " 分钟";
            courseLines.add(line);
            sb.append("- ").append(line).append("\n");
        }

        int totalStudied = progresses.stream().mapToInt(p -> Optional.ofNullable(p.getStudiedSeconds()).orElse(0)).sum();
        Map<String, Object> fallback = ruleAnalysis(user, byCourse.size(), totalDone, totalLessons, totalStudied, courseLines);

        String system = """
                你是企业培训学情分析助手。根据学员学习记录，输出严格 JSON（不要 markdown 代码块），字段：
                summary(string), strengths(string数组), weaknesses(string数组), suggestions(string数组), weeklyPlan(string数组，5条左右每日/每周计划)。
                用简洁专业中文。""";
        Optional<String> ai = llmClient.chat(system, sb.toString());
        Map<String, Object> m = new LinkedHashMap<>(fallback);
        m.put("aiEnabled", llmClient.available());
        m.put("courseCount", byCourse.size());
        m.put("totalStudiedSeconds", totalStudied);
        m.put("completedLessons", totalDone);
        m.put("totalLessons", totalLessons);

        if (ai.isPresent()) {
            Map<String, Object> parsed = parseJsonObject(ai.get());
            if (parsed != null) {
                copyIfPresent(parsed, m, "summary");
                copyList(parsed, m, "strengths");
                copyList(parsed, m, "weaknesses");
                copyList(parsed, m, "suggestions");
                copyList(parsed, m, "weeklyPlan");
                m.put("degraded", false);
                return m;
            }
            m.put("summary", ai.get());
            m.put("degraded", false);
            return m;
        }
        m.put("degraded", true);
        return m;
    }

    private Map<String, Object> ruleAnalysis(User user, int courseCount, int done, int total, int studied, List<String> lines) {
        Map<String, Object> m = new LinkedHashMap<>();
        double rate = total == 0 ? 0 : done * 100.0 / total;
        m.put("summary", "学员「" + Optional.ofNullable(user.getNickname()).orElse(user.getUsername())
                + "」当前参与 " + courseCount + " 门课程，已完成 " + done + "/" + Math.max(total, 1)
                + " 课时（约 " + Math.round(rate) + "%），累计学习 " + (studied / 60) + " 分钟。"
                + (rate >= 70 ? "整体进度良好，建议巩固薄弱章节并拓展相关课程。"
                : rate >= 30 ? "已有一定积累，建议优先完成进度过半的课程。"
                : "起步阶段，建议制定每日学习目标，先完成一门入门课程。"));
        List<String> strengths = new ArrayList<>();
        List<String> weaknesses = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();
        List<String> weekly = new ArrayList<>();
        if (courseCount > 0) strengths.add("已主动开启学习，覆盖 " + courseCount + " 门课程");
        if (done > 0) strengths.add("已完成 " + done + " 个课时，具备持续学习行为");
        if (studied >= 1800) strengths.add("累计学习时长超过 30 分钟，投入度较好");
        if (strengths.isEmpty()) strengths.add("账号已就绪，可立即开始首门课程");
        if (rate < 50) weaknesses.add("课程完成率偏低，存在半途搁置风险");
        if (courseCount > 2 && rate < 40) weaknesses.add("同时学习课程偏多，精力分散");
        if (studied < 600) weaknesses.add("学习时长偏少，建议提高每日学习频率");
        if (weaknesses.isEmpty()) weaknesses.add("暂无明显短板，可进一步挑战进阶课程");
        suggestions.add("优先完成进度最高的一门课，形成闭环成就感");
        suggestions.add("结合 AI 知识点与测验巩固当周所学");
        suggestions.add("利用「我的学习」继续未完成课时，避免重复开新课");
        weekly.add("周一：回顾上周未完成课时，列出本周 2 个目标课时");
        weekly.add("周二至周四：每天至少完成 1 个课时并做简短笔记");
        weekly.add("周五：用智能测验自测本周课程，查漏补缺");
        weekly.add("周末：选择 1 门推荐课程试听，拓展知识面");
        weekly.add("每日打卡：打开「我的学习」检查进度条是否前进");
        m.put("strengths", strengths);
        m.put("weaknesses", weaknesses);
        m.put("suggestions", suggestions);
        m.put("weeklyPlan", weekly);
        return m;
    }

    public Map<String, Object> knowledgePoints(Long courseId, Long lessonId) {
        Course course = courseRepository.findById(courseId).orElseThrow(() -> new BusinessException("课程不存在"));
        String ref = "kp:" + courseId + ":" + (lessonId == null ? "all" : lessonId);
        Optional<Map<String, Object>> cached = readCache("KNOWLEDGE", ref);
        if (cached.isPresent()) {
            Map<String, Object> c = new LinkedHashMap<>(cached.get());
            c.put("cached", true);
            return c;
        }
        String ctx = courseContext(course, lessonId);
        String system = """
                你是课程助教。根据课程信息提炼知识点，输出严格 JSON：
                {"points":[{"title":"...","detail":"..."}]}，points 4～8 条，中文。不要 markdown。""";
        Optional<String> ai = llmClient.chat(system, ctx);
        List<Map<String, Object>> points = new ArrayList<>();
        boolean degraded = true;
        if (ai.isPresent()) {
            Map<String, Object> parsed = parseJsonObject(ai.get());
            if (parsed != null && parsed.get("points") instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof Map<?, ?> pm) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("title", String.valueOf(pm.get("title") != null ? pm.get("title") : "知识点"));
                        row.put("detail", String.valueOf(pm.get("detail") != null ? pm.get("detail") : ""));
                        points.add(row);
                    }
                }
                degraded = points.isEmpty();
            }
        }
        if (points.isEmpty()) {
            points = fallbackPoints(course, lessonId);
            degraded = true;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("points", points);
        m.put("degraded", degraded);
        m.put("courseId", courseId);
        m.put("lessonId", lessonId);
        writeCache("KNOWLEDGE", ref, m);
        return m;
    }

    private List<Map<String, Object>> fallbackPoints(Course course, Long lessonId) {
        List<Map<String, Object>> points = new ArrayList<>();
        points.add(Map.of("title", "课程主题", "detail", Optional.ofNullable(course.getSummary()).orElse(course.getTitle())));
        List<Lesson> lessons = lessonRepository.findByCourseIdOrderBySortOrderAsc(course.getId());
        for (Lesson l : lessons) {
            if (lessonId != null && !lessonId.equals(l.getId())) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("title", l.getTitle());
            row.put("detail", Optional.ofNullable(l.getDescription()).filter(s -> !s.isBlank())
                    .orElse("围绕「" + l.getTitle() + "」掌握核心概念与实践要点。"));
            points.add(row);
            if (points.size() >= 8) break;
        }
        if (course.getOutline() != null && !course.getOutline().isBlank()) {
            points.add(Map.of("title", "章节提纲摘要", "detail", course.getOutline().lines().limit(6).collect(Collectors.joining("；"))));
        }
        return points;
    }

    public Map<String, Object> generateQuiz(Long userId, Long courseId, Long lessonId, Integer count) {
        int n = count == null ? 5 : Math.max(3, Math.min(count, 10));
        Course course = courseRepository.findById(courseId).orElseThrow(() -> new BusinessException("课程不存在"));
        String ctx = courseContext(course, lessonId);
        String system = """
                你是出题老师。根据课程内容生成中文单选题，输出严格 JSON：
                {"questions":[{"id":"q1","question":"...","options":["A","B","C","D"],"answerIndex":0,"explanation":"..."}]}
                questions 恰好 %d 条；options 长度 4；answerIndex 为 0-3。不要 markdown。
                """.formatted(n);
        Optional<String> ai = llmClient.chat(system, ctx);
        List<Map<String, Object>> questions = new ArrayList<>();
        boolean degraded = true;
        if (ai.isPresent()) {
            Map<String, Object> parsed = parseJsonObject(ai.get());
            if (parsed != null && parsed.get("questions") instanceof List<?> list) {
                int i = 0;
                for (Object o : list) {
                    if (!(o instanceof Map<?, ?> qm)) continue;
                    Map<String, Object> q = normalizeQuestion(qm, i++);
                    if (q != null) questions.add(q);
                }
                degraded = questions.isEmpty();
            }
        }
        if (questions.isEmpty()) {
            questions = fallbackQuiz(course, lessonId, n);
            degraded = true;
        }
        String sessionKey = userId + ":" + courseId;
        quizSession.put(sessionKey, questions);
        writeCache("QUIZ", sessionKey, Map.of("questions", questions));

        // strip answers for client? keep for session grade; client needs options only ideally
        // Frontend grades via API so we can include answerIndex; hide is nicer but grade endpoint uses server session.
        List<Map<String, Object>> clientQs = new ArrayList<>();
        for (Map<String, Object> q : questions) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("id", q.get("id"));
            c.put("question", q.get("question"));
            c.put("options", q.get("options"));
            // keep answerIndex for offline fallback; grade API is authoritative
            c.put("answerIndex", q.get("answerIndex"));
            c.put("explanation", q.get("explanation"));
            clientQs.add(c);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("questions", clientQs);
        m.put("degraded", degraded);
        m.put("courseId", courseId);
        m.put("lessonId", lessonId);
        return m;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> gradeQuiz(Long userId, Long courseId, List<Map<String, Object>> answers) {
        String sessionKey = userId + ":" + courseId;
        List<Map<String, Object>> questions = quizSession.get(sessionKey);
        if (questions == null) {
            Optional<Map<String, Object>> cached = readCache("QUIZ", sessionKey);
            if (cached.isPresent() && cached.get().get("questions") instanceof List<?> list) {
                questions = new ArrayList<>();
                for (Object o : list) {
                    if (o instanceof Map<?, ?> m) questions.add(new LinkedHashMap<>((Map<String, Object>) m));
                }
            }
        }
        if (questions == null || questions.isEmpty()) {
            throw new BusinessException("请先生成测验再提交作答");
        }
        Map<String, Integer> byId = new HashMap<>();
        Map<Integer, Integer> byIndex = new HashMap<>();
        if (answers != null) {
            for (int i = 0; i < answers.size(); i++) {
                Map<String, Object> a = answers.get(i);
                Object sel = a.get("selectedIndex");
                int selected = sel == null ? -1 : Integer.parseInt(String.valueOf(sel));
                if (a.get("questionId") != null) byId.put(String.valueOf(a.get("questionId")), selected);
                Object idx = a.get("index");
                if (idx != null) byIndex.put(Integer.parseInt(String.valueOf(idx)), selected);
                else byIndex.putIfAbsent(i, selected);
            }
        }
        int correctCount = 0;
        List<Map<String, Object>> correct = new ArrayList<>();
        List<Map<String, Object>> detail = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            Map<String, Object> q = questions.get(i);
            String qid = String.valueOf(q.get("id"));
            int answerIndex = Integer.parseInt(String.valueOf(q.get("answerIndex")));
            Integer selected = byId.get(qid);
            if (selected == null) selected = byIndex.getOrDefault(i, -1);
            boolean ok = selected != null && selected == answerIndex;
            if (ok) {
                correctCount++;
                correct.add(Map.of("questionId", qid, "index", i));
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("questionId", qid);
            row.put("index", i);
            row.put("selectedIndex", selected);
            row.put("answerIndex", answerIndex);
            row.put("correct", ok);
            row.put("explanation", q.get("explanation"));
            row.put("question", q.get("question"));
            detail.add(row);
        }
        int total = questions.size();
        int score = total == 0 ? 0 : (int) Math.round(correctCount * 100.0 / total);
        String feedbackFallback = score >= 80 ? "表现优秀，已较好掌握本课要点，可继续挑战下一门课程。"
                : score >= 60 ? "基本达标，建议针对错题对应知识点再看一遍视频。"
                : "还需加强，建议重学相关课时并用知识点清单复习后再测。";
        Optional<String> aiFb = llmClient.chat(
                "你是企业培训教练，根据测验结果给一段不超过 120 字的中文反馈，鼓励且具体。",
                "得分 " + score + "/" + total + "，正确 " + correctCount + " 题。明细：" + detail);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("score", score);
        m.put("total", total);
        m.put("correctCount", correctCount);
        m.put("correct", correct);
        m.put("detail", detail);
        m.put("feedback", aiFb.orElse(feedbackFallback));
        m.put("degraded", aiFb.isEmpty());
        return m;
    }

    public Map<String, Object> ask(Long courseId, Long lessonId, String question) {
        if (question == null || question.isBlank()) throw new BusinessException("请输入问题");
        Course course = courseRepository.findById(courseId).orElseThrow(() -> new BusinessException("课程不存在"));
        String ctx = courseContext(course, lessonId);
        String system = """
                你是知行学堂课程助教。仅依据给定课程资料回答学员问题，用简洁专业中文。
                若资料不足请明确说明并给出学习建议。不要编造与课程无关的内容。""";
        Optional<String> ai = llmClient.chat(system, ctx + "\n\n学员问题：" + question.trim());
        String fallback = "根据课程《" + course.getTitle() + "》："
                + Optional.ofNullable(course.getSummary()).orElse("请结合课时目录学习。")
                + " 建议先复习相关课时后再提问更具体的问题。当前 AI 服务繁忙，以上为规则兜底回答。";
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("answer", ai.orElse(fallback));
        m.put("degraded", ai.isEmpty());
        m.put("courseId", courseId);
        m.put("lessonId", lessonId);
        return m;
    }

    public Map<String, Object> lessonDigest(Long lessonId) {
        Lesson lesson = lessonRepository.findById(lessonId).orElseThrow(() -> new BusinessException("课时不存在"));
        Course course = courseRepository.findById(lesson.getCourseId()).orElseThrow(() -> new BusinessException("课程不存在"));
        String ref = "digest:" + lessonId;
        Optional<Map<String, Object>> cached = readCache("DIGEST", ref);
        if (cached.isPresent()) {
            Map<String, Object> c = new LinkedHashMap<>(cached.get());
            c.put("cached", true);
            return c;
        }
        String ctx = "课程：" + course.getTitle() + "\n课时：" + lesson.getTitle()
                + "\n课时简介：" + Optional.ofNullable(lesson.getDescription()).orElse("")
                + "\n课程摘要：" + Optional.ofNullable(course.getSummary()).orElse("")
                + "\n提纲：" + Optional.ofNullable(course.getOutline()).orElse("");
        String system = """
                你是课程助教。为该课时生成摘要，输出严格 JSON：
                {"digest":"120字内摘要","keyTakeaways":["要点1","要点2","要点3"]}
                中文，不要 markdown。""";
        Optional<String> ai = llmClient.chat(system, ctx);
        String digest;
        List<String> takeaways = new ArrayList<>();
        boolean degraded = true;
        if (ai.isPresent()) {
            Map<String, Object> parsed = parseJsonObject(ai.get());
            if (parsed != null) {
                digest = String.valueOf(parsed.getOrDefault("digest", ai.get()));
                if (parsed.get("keyTakeaways") instanceof List<?> list) {
                    for (Object o : list) takeaways.add(String.valueOf(o));
                }
                degraded = false;
            } else {
                digest = ai.get();
                degraded = false;
            }
        } else {
            digest = "本课「" + lesson.getTitle() + "」属于《" + course.getTitle() + "》。"
                    + Optional.ofNullable(lesson.getDescription()).filter(s -> !s.isBlank()).orElse(
                    Optional.ofNullable(course.getSummary()).orElse("请结合视频内容理解核心概念并做好笔记。"));
            takeaways.add("理解「" + lesson.getTitle() + "」的核心概念");
            takeaways.add("结合课程《" + course.getTitle() + "》前后课时建立知识链条");
            takeaways.add("学完后可用智能测验检验掌握程度");
        }
        if (takeaways.isEmpty()) {
            takeaways.add("抓住本课标题所指向的核心主题");
            takeaways.add("对照课程摘要查漏补缺");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("digest", digest);
        m.put("keyTakeaways", takeaways);
        m.put("degraded", degraded);
        m.put("lessonId", lessonId);
        m.put("courseId", course.getId());
        writeCache("DIGEST", ref, m);
        return m;
    }

    public List<Map<String, Object>> recommendWithReason(Long userId) {
        List<Course> courses = recommend(userId);
        List<Map<String, Object>> out = new ArrayList<>();
        Set<Long> learnedCats = new HashSet<>();
        progressRepository.findByUserIdOrderByUpdatedAtDesc(userId).forEach(p ->
                courseRepository.findById(p.getCourseId()).ifPresent(c -> {
                    if (c.getCategoryId() != null) learnedCats.add(c.getCategoryId());
                }));
        for (Course c : courses) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("course", c);
            String reason;
            if (c.getCategoryId() != null && learnedCats.contains(c.getCategoryId())) {
                reason = "与你已学分类相近，便于知识迁移";
            } else if (Boolean.TRUE.equals(c.getRecommended())) {
                reason = "运营精选推荐课程";
            } else if (Boolean.TRUE.equals(c.getHot())) {
                reason = "当前热门，学习人数较多";
            } else {
                reason = "适合拓展学习视野";
            }
            row.put("degraded", true);
            row.put("reason", reason);
            out.add(row);
        }
        return out;
    }

    public List<Course> recommend(Long userId) {
        List<StudyProgress> progresses = progressRepository.findByUserIdOrderByUpdatedAtDesc(userId);
        Set<Long> learnedCats = new HashSet<>();
        Set<Long> learnedCourses = new HashSet<>();
        for (StudyProgress p : progresses) {
            learnedCourses.add(p.getCourseId());
            courseRepository.findById(p.getCourseId()).ifPresent(c -> {
                if (c.getCategoryId() != null) learnedCats.add(c.getCategoryId());
            });
        }
        List<Course> all = courseRepository.searchPublished("PUBLISHED", null, null,
                org.springframework.data.domain.PageRequest.of(0, 50)).getContent();
        List<Course> scored = new ArrayList<>();
        for (Course c : all) {
            if (learnedCourses.contains(c.getId())) continue;
            scored.add(c);
        }
        scored.sort((a, b) -> {
            int sa = (a.getCategoryId() != null && learnedCats.contains(a.getCategoryId()) ? 2 : 0)
                    + (Boolean.TRUE.equals(a.getRecommended()) ? 1 : 0)
                    + (Boolean.TRUE.equals(a.getHot()) ? 1 : 0);
            int sb = (b.getCategoryId() != null && learnedCats.contains(b.getCategoryId()) ? 2 : 0)
                    + (Boolean.TRUE.equals(b.getRecommended()) ? 1 : 0)
                    + (Boolean.TRUE.equals(b.getHot()) ? 1 : 0);
            return Integer.compare(sb, sa);
        });
        List<Course> top = scored.stream().limit(6).toList();
        if (top.isEmpty()) {
            return courseRepository.findTop8ByStatusAndRecommendedTrueOrderBySortOrderAsc("PUBLISHED");
        }
        return top;
    }

    @Transactional
    public Map<String, Object> courseAssist(Long courseId, boolean apply) {
        Course course = courseRepository.findById(courseId).orElseThrow(() -> new BusinessException("课程不存在"));
        List<Lesson> lessons = lessonRepository.findByCourseIdOrderBySortOrderAsc(courseId);
        StringBuilder sb = new StringBuilder();
        sb.append("课程标题：").append(course.getTitle()).append("\n简介：").append(course.getSummary()).append("\n课时：\n");
        for (Lesson l : lessons) {
            sb.append(l.getSortOrder()).append(". ").append(l.getTitle()).append("\n");
        }
        String fallbackSummary = "本课程共 " + lessons.size() + " 个课时，涵盖「" + course.getTitle() + "」核心内容。";
        String fallbackOutline = lessons.stream().map(l -> "- " + l.getTitle()).collect(Collectors.joining("\n"));
        Optional<String> summary = llmClient.chat(
                "你是课程内容编辑，请用中文生成 120 字以内的课程摘要。", sb.toString());
        Optional<String> outline = llmClient.chat(
                "你是课程内容编辑，请用中文输出章节提纲（条目列表），简洁专业。", sb.toString());
        String s = summary.orElse(fallbackSummary);
        String o = outline.orElse(fallbackOutline);
        if (apply) {
            course.setAiSummary(s);
            course.setOutline(o);
            if (course.getSummary() == null || course.getSummary().isBlank()) {
                course.setSummary(s);
            }
            courseRepository.save(course);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("summary", s);
        m.put("outline", o);
        m.put("applied", apply);
        m.put("degraded", summary.isEmpty() || outline.isEmpty());
        return m;
    }

    public Map<String, Object> newsAssist(Long newsId, boolean apply) {
        News news = newsRepository.findById(newsId).orElseThrow(() -> new BusinessException("新闻不存在"));
        String body = "标题：" + news.getTitle() + "\n内容：" + Optional.ofNullable(news.getContent()).orElse("");
        Optional<String> summary = llmClient.chat("用中文写 80 字以内新闻导语。", body);
        String s = summary.orElse(Optional.ofNullable(news.getSummary()).filter(x -> !x.isBlank())
                .orElse(news.getTitle()));
        if (apply) {
            news.setSummary(s);
            newsRepository.save(news);
        }
        return Map.of("summary", s, "applied", apply, "degraded", summary.isEmpty());
    }

    private String courseContext(Course course, Long lessonId) {
        StringBuilder sb = new StringBuilder();
        sb.append("课程标题：").append(course.getTitle()).append("\n");
        sb.append("摘要：").append(Optional.ofNullable(course.getSummary()).orElse("")).append("\n");
        sb.append("AI摘要：").append(Optional.ofNullable(course.getAiSummary()).orElse("")).append("\n");
        sb.append("提纲：").append(Optional.ofNullable(course.getOutline()).orElse("")).append("\n");
        sb.append("介绍：").append(Optional.ofNullable(course.getDescription()).orElse("")).append("\n课时列表：\n");
        for (Lesson l : lessonRepository.findByCourseIdOrderBySortOrderAsc(course.getId())) {
            if (lessonId != null && !lessonId.equals(l.getId())) continue;
            sb.append("- ").append(l.getTitle());
            if (l.getDescription() != null) sb.append("：").append(l.getDescription());
            sb.append("\n");
        }
        return sb.toString();
    }

    private Map<String, Object> normalizeQuestion(Map<?, ?> qm, int i) {
        Object opts = qm.get("options");
        List<String> options = new ArrayList<>();
        if (opts instanceof List<?> list) {
            for (Object o : list) options.add(String.valueOf(o));
        }
        while (options.size() < 4) options.add("选项" + (options.size() + 1));
        if (options.size() > 4) options = options.subList(0, 4);
        int answerIndex = 0;
        try {
            answerIndex = Integer.parseInt(String.valueOf(qm.get("answerIndex")));
        } catch (Exception ignored) {}
        answerIndex = Math.max(0, Math.min(3, answerIndex));
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("id", qm.get("id") != null ? String.valueOf(qm.get("id")) : "q" + (i + 1));
        q.put("question", String.valueOf(qm.get("question") != null ? qm.get("question") : ("题目" + (i + 1))));
        q.put("options", options);
        q.put("answerIndex", answerIndex);
        q.put("explanation", String.valueOf(qm.get("explanation") != null ? qm.get("explanation") : "请结合课程内容理解。"));
        return q;
    }

    private List<Map<String, Object>> fallbackQuiz(Course course, Long lessonId, int n) {
        List<Lesson> lessons = lessonRepository.findByCourseIdOrderBySortOrderAsc(course.getId());
        List<Map<String, Object>> qs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Lesson l = lessons.isEmpty() ? null : lessons.get(i % lessons.size());
            String topic = l != null ? l.getTitle() : course.getTitle();
            Map<String, Object> q = new LinkedHashMap<>();
            q.put("id", "q" + (i + 1));
            q.put("question", "关于「" + topic + "」，下列说法更恰当的是？");
            q.put("options", List.of(
                    "应结合课程《" + course.getTitle() + "》理解其核心要点",
                    "与本课程无关，可忽略",
                    "只需记忆标题无需实践",
                    "学习进度不影响掌握程度"
            ));
            q.put("answerIndex", 0);
            q.put("explanation", "规则兜底题：正确选项强调结合本课程内容理解「" + topic + "」。");
            qs.add(q);
        }
        return qs;
    }

    private Map<String, Object> parseJsonObject(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.startsWith("```")) {
            int start = s.indexOf('{');
            int end = s.lastIndexOf('}');
            if (start >= 0 && end > start) s = s.substring(start, end + 1);
        }
        try {
            JsonNode node = objectMapper.readTree(s);
            if (!node.isObject()) {
                // try extract first {...}
                int start = s.indexOf('{');
                int end = s.lastIndexOf('}');
                if (start >= 0 && end > start) {
                    node = objectMapper.readTree(s.substring(start, end + 1));
                }
            }
            if (!node.isObject()) return null;
            return objectMapper.convertValue(node, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.debug("parse AI json failed: {}", e.getMessage());
            return null;
        }
    }

    private void copyIfPresent(Map<String, Object> src, Map<String, Object> dst, String key) {
        if (src.get(key) != null) dst.put(key, String.valueOf(src.get(key)));
    }

    private void copyList(Map<String, Object> src, Map<String, Object> dst, String key) {
        Object v = src.get(key);
        if (v instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object o : list) out.add(String.valueOf(o));
            if (!out.isEmpty()) dst.put(key, out);
        }
    }

    private Optional<Map<String, Object>> readCache(String type, String ref) {
        try {
            return aiCacheRepository.findByCacheTypeAndRefKey(type, ref).map(c -> {
                try {
                    return objectMapper.readValue(c.getContentJson(), new TypeReference<Map<String, Object>>() {});
                } catch (Exception e) {
                    return null;
                }
            }).filter(Objects::nonNull);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private void writeCache(String type, String ref, Map<String, Object> data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            AiCache row = aiCacheRepository.findByCacheTypeAndRefKey(type, ref)
                    .orElse(AiCache.builder().cacheType(type).refKey(ref).build());
            row.setContentJson(json);
            aiCacheRepository.save(row);
        } catch (Exception e) {
            log.debug("ai cache write skipped: {}", e.getMessage());
        }
    }
}
