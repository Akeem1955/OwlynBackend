package com.owlynbackend.internal.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

public class AssessorDTOs {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeminiReportJson {
        private Integer score;
        private String behavioralNotes;
        private String codeOutput;
        private Map<String, Object> behaviorFlags;
    }
}