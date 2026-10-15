package com.vlp.learn.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

public class LearnDtos {
    @Data
    public static class ProgressReport {
        @NotNull
        private Long lessonId;
        @NotNull @Min(0)
        private Integer positionSeconds;
        /** incremental studied seconds since last report */
        @Min(0)
        private Integer deltaSeconds;
        private Boolean heartbeat;
        /** browser reported media duration; used to correct lesson.durationSeconds */
        @Min(0)
        private Integer videoDurationSeconds;
        /** true when HTML5 video fired ended */
        private Boolean ended;
    }

    @Data
    public static class NoteReq {
        @NotNull
        private Long lessonId;
        @NotNull @Min(0)
        private Integer positionSeconds;
        @NotNull
        private String content;
    }

    @Data
    public static class BookmarkReq {
        @NotNull
        private Long lessonId;
        @NotNull @Min(0)
        private Integer positionSeconds;
        private String title;
    }
}
