package com.owlynbackend.internal.dto;



import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class InterviewDTOs {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GenerateQuestionsReq {
        private String jobTitle;
        private String instructions; // Optional context like "Focus on AOP"
        private Integer questionCount; // Optional (User freedom)
    }

    @Data
    @AllArgsConstructor
    public static class GenerateQuestionsRes {
        private String draftedQuestions;
    }
}