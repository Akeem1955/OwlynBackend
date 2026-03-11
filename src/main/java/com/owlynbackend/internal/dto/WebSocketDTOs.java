package com.owlynbackend.internal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class WebSocketDTOs {

    // Incoming from Electron (Unified Capture)
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClientMediaPayload {
        private String event; // "MEDIA" or "STOP"
        private String videoFrame; // Base64 JPEG (The entire screen)
        private String audioChunk; // Base64 PCM Audio
        private String codeEditorText; // Raw string of the code
    }

    // Outgoing to Electron (Audio & UI Commands)
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServerCommandPayload {
        private String type; // "AUDIO", "TOOL_HIGHLIGHT", "PROCTOR_WARNING","TOOL_POST_QUESTION"
        private String audioData; // Base64 PCM for David to hear
        private Integer errorLine; // Line number to highlight
        private String message; // Proctor warning text
    }
}