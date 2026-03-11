package com.owlynbackend.services;



import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import com.owlynbackend.internal.dto.InterviewDTOs.GenerateQuestionsReq;
import com.owlynbackend.internal.dto.InterviewDTOs.GenerateQuestionsRes;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GeminiAgentService {

    private final Client genaiClient;

    @Retry(name = "geminiApi")
    @CircuitBreaker(name = "geminiApi", fallbackMethod = "fallbackGenerateQuestions")
    public GenerateQuestionsRes draftQuestions(GenerateQuestionsReq req) {

        // 1. Build the AI Prompt
        String prompt = getString(req);

        // 2. Call Gemini 2.5 Flash using the Native ADK/GenAI SDK
        GenerateContentResponse response = genaiClient.models.generateContent(
                "gemini-3-flash-preview",
                prompt,
                null // Optional config
        );

        // 3. Return the text using the native accessor
        return new GenerateQuestionsRes(response.text());
    }

    private static @NonNull String getString(GenerateQuestionsReq req) {
        String countText = (req.getQuestionCount() != null && req.getQuestionCount() > 0)
                ? "Generate exactly " + req.getQuestionCount() + " "
                : "Generate a standard number of ";

        return "Act as an expert technical recruiter. " + countText +
                "highly technical interview questions for a " + req.getJobTitle() + " role. " +
                (req.getInstructions() != null ? "Strictly follow these instructions: " + req.getInstructions() : "") +
                " Only return the questions in clear, readable text. Do not return markdown blocks.";
    }

    // The Resilience4j Fallback Method
    public GenerateQuestionsRes fallbackGenerateQuestions(GenerateQuestionsReq req, Throwable t) {
        // If the native SDK throws a timeout or rate-limit error, we catch it here seamlessly.
        String gracefulMessage = "AI Generation is currently unavailable due to high traffic or network issues. " +
                "Please type your custom interview questions manually in this box.";
        return new GenerateQuestionsRes(gracefulMessage);
    }
}
