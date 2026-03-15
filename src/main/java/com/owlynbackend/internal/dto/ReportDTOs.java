package com.owlynbackend.internal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

public class ReportDTOs {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddFeedbackReq {
        private String humanFeedback;
        private String decision;
    }

    @Data
    @Builder
    public static class ReportRes {
        private UUID reportId;
        private UUID interviewId;
        private String candidateEmail;
        private String candidateName;
        private Integer score;
        private String behavioralNotes;
        private String codeOutput;
        private Map<String, Object> behaviorFlags; // JSON payload from Gemini
        private String humanFeedback;
        private String finalDecision; // NEW: Send the decision back to UI
    }
}