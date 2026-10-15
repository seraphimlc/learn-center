package com.vlp.learn.controller;

import com.vlp.common.ApiResponse;
import com.vlp.learn.dto.LearnDtos.*;
import com.vlp.learn.entity.*;
import com.vlp.learn.service.LearnService;
import com.vlp.upload.MediaSigningService;
import com.vlp.security.AuthContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/learn")
@RequiredArgsConstructor
public class LearnController {
    private final LearnService learnService;
    private final MediaSigningService mediaSigningService;


    /** Mint short-lived signed play URL (login required). Does not expose permanent OSS URLs. */
    @GetMapping("/play-url/{lessonId}")
    public ApiResponse<Map<String, Object>> playUrl(@PathVariable Long lessonId) {
        return ApiResponse.ok(mediaSigningService.mintPlayUrl(AuthContext.current(), lessonId));
    }

    @PostMapping("/progress")
    public ApiResponse<StudyProgress> progress(@Valid @RequestBody ProgressReport req) {
        return ApiResponse.ok(learnService.reportProgress(AuthContext.current(), req));
    }

    @GetMapping("/my")
    public ApiResponse<List<Map<String, Object>>> my() {
        return ApiResponse.ok(learnService.myLearning(AuthContext.current().getId()));
    }

    @PostMapping("/notes")
    public ApiResponse<StudyNote> addNote(@Valid @RequestBody NoteReq req) {
        return ApiResponse.ok(learnService.addNote(AuthContext.current(), req));
    }

    @GetMapping("/notes")
    public ApiResponse<List<StudyNote>> notes(@RequestParam(required = false) Long lessonId) {
        return ApiResponse.ok(learnService.notes(AuthContext.current().getId(), lessonId));
    }

    @DeleteMapping("/notes/{id}")
    public ApiResponse<Void> delNote(@PathVariable Long id) {
        learnService.deleteNote(AuthContext.current(), id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/bookmarks")
    public ApiResponse<Bookmark> addBm(@Valid @RequestBody BookmarkReq req) {
        return ApiResponse.ok(learnService.addBookmark(AuthContext.current(), req));
    }

    @GetMapping("/bookmarks")
    public ApiResponse<List<Bookmark>> bms(@RequestParam(required = false) Long lessonId) {
        return ApiResponse.ok(learnService.bookmarks(AuthContext.current().getId(), lessonId));
    }

    @DeleteMapping("/bookmarks/{id}")
    public ApiResponse<Void> delBm(@PathVariable Long id) {
        learnService.deleteBookmark(AuthContext.current(), id);
        return ApiResponse.ok(null);
    }
}
