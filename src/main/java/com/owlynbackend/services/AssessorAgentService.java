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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AssessorAgentService {

    private static final Logger log = LoggerFactory.getLogger(AssessorAgentService.class);

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
                    "If the transcript indicates the interview was terminated for non-serious behavior, refusal to answer, trolling, or repeated policy violations, " +
                    "you must reflect that clearly with a low score and explicit behavior flags/details. " +
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
                GeminiReportJson reportData = parseGeminiReportSafely(jsonOutput);

            // THE TRAFFIC SWITCH: Where does the data go?
            if (interview.getMode() == InterviewMode.STANDARD) {
                // Enterprise B2B Flow: Save to PostgreSQL
                InterviewReport report = InterviewReport.builder()
                        .interview(interview)
                    .candidateEmail(interview.getAccessCode() != null ? interview.getAccessCode() : accessCode)
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
            log.error("Failed to generate/save report for interview {}", interviewIdStr, e);
        }
    }

    private GeminiReportJson parseGeminiReportSafely(String rawOutput) {
        try {
            return normalizeReportDto(objectMapper.readValue(rawOutput, GeminiReportJson.class));
        } catch (Exception ignored) {
        }

        try {
            int first = rawOutput.indexOf('{');
            int last = rawOutput.lastIndexOf('}');
            if (first >= 0 && last > first) {
                String candidateJson = rawOutput.substring(first, last + 1);
                return normalizeReportDto(objectMapper.readValue(candidateJson, GeminiReportJson.class));
            }
        } catch (Exception ignored) {
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> generic = objectMapper.readValue(rawOutput, Map.class);
            Integer score = safeToInt(generic.get("score"));
            String behavioralNotes = safeToString(generic.get("behavioralNotes"));
            String codeOutput = safeToString(generic.get("codeOutput"));
            Map<String, Object> behaviorFlags = safeToMap(generic.get("behaviorFlags"));

            return normalizeReportDto(new GeminiReportJson(score, behavioralNotes, codeOutput, behaviorFlags));
        } catch (Exception ignored) {
        }

        Map<String, Object> fallbackFlags = new HashMap<>();
        fallbackFlags.put("cheating_warnings_count", 0);
        fallbackFlags.put("details", "Unable to parse structured report from model response.");
        return new GeminiReportJson(
                50,
                "Automated assessment completed with partial data. Manual review recommended.",
                "Code output could not be fully parsed from model response.",
                fallbackFlags
        );
    }

    private GeminiReportJson normalizeReportDto(GeminiReportJson dto) {
        Integer score = dto.getScore() == null ? 50 : Math.max(0, Math.min(100, dto.getScore()));
        String behavioralNotes = dto.getBehavioralNotes() == null || dto.getBehavioralNotes().isBlank()
                ? "No behavioral notes were returned by the model."
                : dto.getBehavioralNotes();
        String codeOutput = dto.getCodeOutput() == null || dto.getCodeOutput().isBlank()
                ? "No code quality summary was returned by the model."
                : dto.getCodeOutput();
        Map<String, Object> behaviorFlags = dto.getBehaviorFlags() != null ? dto.getBehaviorFlags() : new HashMap<>();
        behaviorFlags.putIfAbsent("cheating_warnings_count", 0);
        behaviorFlags.putIfAbsent("details", "No additional behavioral flags.");

        return new GeminiReportJson(score, behavioralNotes, codeOutput, behaviorFlags);
    }

    private Integer safeToInt(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    private String safeToString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> safeToMap(Object value) {
        if (value instanceof Map<?, ?> mapValue) {
            Map<String, Object> mapped = new HashMap<>();
            mapValue.forEach((k, v) -> mapped.put(String.valueOf(k), v));
            return mapped;
        }
        return new HashMap<>();
    }
}