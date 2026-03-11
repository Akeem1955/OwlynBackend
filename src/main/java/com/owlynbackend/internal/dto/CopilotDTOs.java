package com.owlynbackend.internal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class CopilotDTOs {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CopilotReq {
        private String code;
        private String language;
        private Integer cursorPosition; // The exact character index where David stopped typing
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CopilotRes {
        private String suggestion;
    }
}