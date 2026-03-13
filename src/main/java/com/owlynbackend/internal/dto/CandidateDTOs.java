package com.owlynbackend.internal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

public class CandidateDTOs {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidateCodeReq {
        private String code;
    }

    @Data
    @Builder
    public static class ValidateCodeRes {
        private String token; // The Guest JWT
        private String livekitToken;
        private String candidateName;
        private String personaName;
        private UUID interviewId;
        private String title;
        private Integer durationMinutes;
        private Map<String, Boolean> toolsEnabled;
        private ValidateCodeConfigRes config;
    }

    @Data
    @Builder
    public static class ValidateCodeConfigRes {
        private Map<String, Boolean> toolsEnabled;
    }
}