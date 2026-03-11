package com.owlynbackend.services;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import com.owlynbackend.internal.dto.CopilotDTOs.CopilotReq;
import com.owlynbackend.internal.dto.CopilotDTOs.CopilotRes;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CopilotService {

    @Value("${gemini.api.key}")
    private String apiKey;

    private Client genaiClient;

    @PostConstruct
    public void init() {
        this.genaiClient = Client.builder().apiKey(apiKey).build();
    }

    @Retry(name = "geminiApi")
    @CircuitBreaker(name = "geminiApi", fallbackMethod = "fallbackCopilot")
    public CopilotRes generateSuggestion(CopilotReq req) {

        // 1. Split the code so Gemini knows exactly where the cursor is
        String codeBeforeCursor = req.getCode().substring(0, req.getCursorPosition());
        String codeAfterCursor = req.getCode().substring(req.getCursorPosition());

        // 2. Strict Prompt Engineering for Inline Completion
        String prompt = "You are an elite inline code completion engine. " +
                "Language: " + req.getLanguage() + ". " +
                "Here is the code BEFORE the cursor:\n" + codeBeforeCursor + "\n\n" +
                "Here is the code AFTER the cursor:\n" + codeAfterCursor + "\n\n" +
                "Generate ONLY the exact code that should be inserted at the cursor. " +
                "Do not explain. Do not use Markdown backticks (```). Return raw code only. " +
                "Limit your response to 1-3 lines max.";

        // 3. Native SDK Call to Gemini 3.0 Flash
        GenerateContentResponse response = genaiClient.models.generateContent(
                "gemini-3.0-flash-preview",
                prompt,
                null
        );

        String ghostText = response.text() != null ? response.text().trim() : "";

        return CopilotRes.builder()
                .suggestion(ghostText)
                .build();
    }

    // Graceful Fallback: Return empty string on timeout so the Editor doesn't break
    public CopilotRes fallbackCopilot(CopilotReq req, Throwable t) {
        return new CopilotRes("");
    }
}