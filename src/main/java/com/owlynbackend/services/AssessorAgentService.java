package com.owlynbackend.services;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.owlynbackend.internal.dto.AssessorDTOs.GeminiReportJson;
import com.owlynbackend.internal.model.Interview;
import com.owlynbackend.internal.model.InterviewReport;
import com.owlynbackend.internal.model.enums.InterviewMode;
import com.owlynbackend.internal.model.enums.InterviewStatus;
import com.owlynbackend.internal.repository.InterviewReportRepository;
import com.owlynbackend.internal.repository.InterviewRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AssessorAgentService {

    @Value("${gemini.api.key}")
    private String apiKey;

    private final InterviewRepository interviewRepository;
    private final InterviewReportRepository interviewReportRepository;
    private final ObjectMapper objectMapper;
    private Client genaiClient;
    private final StringRedisTemplate stringRedisTemplate;

    @PostConstruct
    public void init() {
        this.genaiClient = Client.builder().apiKey(apiKey).build();
    }

    @Async // Runs in the background!
    @Transactional
    public void generateAndSaveReport(String interviewIdStr, String accessCode, String transcript, String finalCode) {
        try {
            UUID interviewId = UUID.fromString(interviewIdStr);
            Interview interview = interviewRepository.findById(interviewId)
                    .orElseThrow(() -> new RuntimeException("Interview not found for reporting."));

            // 1. Strict Prompt Engineering for Structured JSON Output
            String prompt = "You are Agent 4, an elite Lead Technical Assessor. " +
                    "Review the following interview transcript and the candidate's final code. " +
                    "Evaluate the candidate out of 100. " +
                    "You MUST return your response as a raw, valid JSON object matching exactly this schema:\n" +
                    "{\n" +
                    "  \"score\": integer,\n" +
                    "  \"behavioralNotes\": \"string summary of their communication and problem-solving\",\n" +
                    "  \"codeOutput\": \"string summary of code quality\",\n" +
                    "  \"behaviorFlags\": {\"cheating_warnings_count\": integer, \"details\": \"string\"}\n" +
                    "}\n\n" +
                    "--- INTERVIEW TRANSCRIPT ---\n" + transcript + "\n\n" +
                    "--- FINAL CODE ---\n" + finalCode;

            // 2. Force the model to output JSON natively
            GenerateContentConfig config = GenerateContentConfig.builder()
                    .responseMimeType("application/json")
                    .build();

            // 3. Call Gemini 3.1 Pro (The Heavyweight Reasoner)
            GenerateContentResponse response = genaiClient.models.generateContent(
                    "gemini-3.1-pro-preview",
                    prompt,
                    config
            );

            String jsonOutput = response.text() != null ? response.text().trim() : "{}";

            // 4. Deserialize the JSON into our DTO safely using Jackson
            // THE TRAFFIC SWITCH: Where does the data go?
            if (interview.getMode() == InterviewMode.STANDARD) {
                // Enterprise B2B Flow: Save to PostgreSQL
                GeminiReportJson reportData = objectMapper.readValue(jsonOutput, GeminiReportJson.class);
                InterviewReport report = InterviewReport.builder()
                        .interview(interview)
                        .candidateEmail(accessCode)
                        .score(reportData.getScore())
                        .behavioralNotes(reportData.getBehavioralNotes())
                        .codeOutput(reportData.getCodeOutput())
                        .behaviorFlags(reportData.getBehaviorFlags())
                        .build();

                interviewReportRepository.save(report);
            } else {
                // B2C Educational Flow: Save to Redis temporarily (15 minutes)
                // The frontend will ping the server to fetch this JSON, then it vanishes forever.
                String redisKey = "ephemeral_report:" + interviewIdStr;
                stringRedisTemplate.opsForValue().set(redisKey, jsonOutput, Duration.ofMinutes(15));
            }


            // 6. Close the interview so the code cannot be reused
            interview.setStatus(InterviewStatus.COMPLETED);
            interviewRepository.save(interview);

        } catch (Exception e) {
            // AOP catches the execution, but we log the stack trace locally just in case Gemini's JSON structure breaks
            e.printStackTrace();
        }
    }
}