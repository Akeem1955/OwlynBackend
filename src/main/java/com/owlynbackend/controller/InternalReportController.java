package com.owlynbackend.controller;

import com.owlynbackend.internal.model.Interview;
import com.owlynbackend.internal.repository.InterviewRepository;
import com.owlynbackend.services.AssessorAgentService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/internal/reports")
@RequiredArgsConstructor
public class InternalReportController {

    private final AssessorAgentService assessorAgentService;
    // Inject InterviewRepository in your constructor
    private final InterviewRepository interviewRepository;

    @Value("${internal.python.secret}")
    private String expectedSecret;

    @Data
    public static class TriggerReportReq {
        private String interviewId;
        private String accessCode;
        private String transcript;
        private String finalCode;
    }

    @PostMapping("/trigger")
    public ResponseEntity<String> triggerAssessor(
            @RequestHeader("X-Internal-Token") String secretToken,
            @RequestBody TriggerReportReq req) {

        // Basic security so only your Python worker can call this
        if (!expectedSecret.equals(secretToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid Internal Token");
        }

        // Fire the background @Async Gemini 3.1 Pro task exactly like we did before!
        assessorAgentService.generateAndSaveReport(
                req.getInterviewId(),
                req.getAccessCode(),
                req.getTranscript(),
                req.getFinalCode()
        );

        return ResponseEntity.ok("Assessor Triggered Successfully");
    }









    // ADD THIS ENDPOINT:
    @GetMapping("/interviews/{interviewId}/config")
    public ResponseEntity<?> getInterviewConfig(
            @RequestHeader("X-Internal-Token") String secretToken,
            @PathVariable String interviewId) {

        if (!expectedSecret.equals(secretToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid Internal Token");
        }

        Interview interview = interviewRepository.findById(UUID.fromString(interviewId))
                .orElse(null);

        if (interview == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Interview not found");
        }

        // Build the system prompt exactly like we did in the old monolith
        StringBuilder instructions = new StringBuilder();
        if ("TUTOR".equals(interview.getMode().name())) {
            instructions.append("You are Owlyn, a friendly, patient human tutor. Do not give direct answers; instead, guide the user step-by-step.");
        } else {
            instructions.append("You are the lead technical interviewer. Conduct a ").append(interview.getDurationMinutes()).append("-minute interview. ");
            if (interview.getPersona() != null) {
                instructions.append("Your name is ").append(interview.getPersona().getName()).append(". ")
                        .append("Tone: ").append(interview.getPersona().getTone()).append(". ")
                        .append("Knowledge Base:\n").append(interview.getPersona().getKnowledgeBaseText()).append("\n\n");
            } else {
                instructions.append(interview.getAiInstructions()).append("\n\n");
            }
            instructions.append("You MUST ask these questions:\n").append(interview.getGeneratedQuestions());
        }

        return ResponseEntity.ok(Map.of(
                "mode", interview.getMode().name(),
                "systemPrompt", instructions.toString()
        ));
    }
}