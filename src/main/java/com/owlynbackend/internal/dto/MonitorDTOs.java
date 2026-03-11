package com.owlynbackend.internal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class MonitorDTOs {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonitorRelayRes {
        private String type; // "MEDIA" or "ALERT"
        private String videoFrame; // Base64 JPEG
        private String codeEditorText; // Raw code
        private String alertMessage; // E.g., "Phone detected"
    }
}