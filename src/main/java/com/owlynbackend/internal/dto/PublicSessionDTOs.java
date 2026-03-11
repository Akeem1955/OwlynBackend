package com.owlynbackend.internal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

public class PublicSessionDTOs {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeneratePracticeReq {
        private String topic; // e.g., "System Design - Microservices"
        private String difficulty; // e.g., "Hard"
        private Integer durationMinutes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PublicSessionRes {
        private String token; // The Guest JWT
        private String livekitToken;
        private UUID interviewId;
        private String accessCode;
        private String mode; // "PRACTICE" or "TUTOR"
    }
}