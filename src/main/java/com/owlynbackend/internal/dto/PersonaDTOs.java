package com.owlynbackend.internal.dto;



import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

public class PersonaDTOs {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreatePersonaReq {
        private String name;
        private String roleTitle;
        private Integer empathyScore;
        private Integer analyticalDepth;
        private Integer directnessScore;
        private String tone;
        private List<String> domainExpertise;
    }

    @Data
    @Builder
    public static class PersonaRes {
        private UUID id;
        private String name;
        private String roleTitle;
        private Integer empathyScore;
        private Integer analyticalDepth;
        private Integer directnessScore;
        private String tone;
        private List<String> domainExpertise;
        private boolean hasKnowledgeBase; // Frontend uses this to show a "File Attached" icon
    }
}
