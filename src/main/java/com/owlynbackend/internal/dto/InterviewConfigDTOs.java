package com.owlynbackend.internal.dto;



import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

public class InterviewConfigDTOs {


    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InterviewCreatedRes {
        private UUID interviewId;
        private String title;
        private String accessCode; // The Golden Ticket (e.g., "839201")
        private String status;
    }

    // Inside InterviewConfigDTOs.java

    @Data
    public static class CreateInterviewReq {
        private String title;
        private Integer durationMinutes;
        private Map<String, Boolean> toolsEnabled;

        // Either they use a saved Persona, OR they type manual instructions
        private UUID personaId; // <--- ADD THIS LINE
        private String aiInstructions;

        private String generatedQuestions;
    }
}