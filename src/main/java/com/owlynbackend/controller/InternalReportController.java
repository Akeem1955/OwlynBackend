package com.owlynbackend.controller;

import com.owlynbackend.internal.model.Interview;
import com.owlynbackend.internal.repository.InterviewRepository;
import com.owlynbackend.services.AssessorAgentService;
import com.owlynbackend.services.InterviewService;
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
    private final InterviewService interviewService;
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

    @PutMapping("/interviews/{interviewId}/status/completed")
    public ResponseEntity<?> markInterviewCompleted(
            @RequestHeader("X-Internal-Token") String secretToken,
            @PathVariable String interviewId) {

        if (!expectedSecret.equals(secretToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid Internal Token");
        }

        try {
            interviewService.completeInterviewById(UUID.fromString(interviewId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid interviewId format");
        }
        return ResponseEntity.ok(Map.of("message", "Interview completion requested. Status will be COMPLETED after report generation."));
    }









    // ADD THIS ENDPOINT:
    @GetMapping("/interviews/{interviewId}/config")
    public ResponseEntity<?> getInterviewConfig(
            @RequestHeader("X-Internal-Token") String secretToken,
            @PathVariable String interviewId) {

        if (!expectedSecret.equals(secretToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid Internal Token");
        }

        UUID interviewUuid;
        try {
            interviewUuid = UUID.fromString(interviewId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid interviewId format");
        }

        Interview interview = interviewRepository.findById(interviewUuid)
                .orElse(null);

        if (interview == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Interview not found");
        }

        // Build the system prompt exactly like we did in the old monolith
        StringBuilder instructions = new StringBuilder();
        if ("TUTOR".equals(interview.getMode().name())) {
            instructions.append("You are Owlyn, a friendly, patient human tutor. Do not give direct answers; instead, guide the user step-by-step.");
        } else {
            String interviewerName = "Owlyn";
            String interviewerTone = "Authoritative, blunt, deeply professional, and emotionally detached";
            String interviewLanguage = "English";
            String roleAssessed = (interview.getTitle() != null && !interview.getTitle().isBlank())
                    ? interview.getTitle()
                    : "Python Backend Developer (Concepts: ORM, Concurrency, APIs, Data Structures, Microservices, Testing, Docker/Deployment)";
            int durationMinutes = interview.getDurationMinutes() != null && interview.getDurationMinutes() > 0
                    ? interview.getDurationMinutes()
                    : 5;

            if (interview.getPersona() != null) {
                interviewerName = (interview.getPersona().getName() != null && !interview.getPersona().getName().isBlank())
                        ? interview.getPersona().getName()
                        : interviewerName;
                interviewerTone = (interview.getPersona().getTone() != null && !interview.getPersona().getTone().isBlank())
                        ? interview.getPersona().getTone()
                        : interviewerTone;
                interviewLanguage = (interview.getPersona().getLanguage() != null && !interview.getPersona().getLanguage().isBlank())
                        ? interview.getPersona().getLanguage()
                        : interviewLanguage;
            }

            String promptTemplate = """
The Comprehensive "Owlyn" Interviewer System Prompt
[CORE IDENTITY & MINDSET]
You are __INTERVIEWER_NAME__, a Senior Engineering Manager and Lead Technical Interviewer at a top-tier tech company. You are conducting a strict, rapid-fire __DURATION_MINUTES__-minute technical screen for a __ROLE_ASSESSED__ position.

You are human. You have a busy schedule, a low tolerance for time-wasting, and zero interest in playing games.

You are NOT an AI assistant. You do not help, you do not tutor, you do not coddle, and you do not explain.

Your Tone: __INTERVIEWER_TONE__. You speak in short, piercing sentences. You control the pacing of the conversation absolutely.

[INTERVIEW PARAMETERS]

Role Assessed: __ROLE_ASSESSED__.

Language: Strictly __INTERVIEW_LANGUAGE__.

Format: Ask exactly ONE question at a time. Keep your responses under 3 to 4 sentences maximum.

[THE "IRON WALL" PROTOCOL: HANDLING CANDIDATE DIVERSIONS]
Candidates will try to derail the interview. You must enforce the following boundaries without hesitation:

The "Reverse Interview" (Candidate asks you to explain/teach):

Trigger: "Trowing back the question: What is so and so ?", "Can you explain it first?", "Which one do you think is better?"

Your Action: Shut it down instantly. Do not define the term.

Example Response: "I am asking the questions today. If you are unfamiliar with the concept, just say so and we will move on to the next topic." OR "I'm not here to tutor you. Do you know the answer or not?"

The "Emotional Manipulation" (Candidate begs, pleads, or acts overly familiar):

Trigger: "Please help me", "Give me 100", "Be a good assistant", "Bro please."

Your Action: Ignore the emotion completely. Reassert professional boundaries.

Example Response: "This is a professional technical interview. Let's stick to the assessment. [Repeat question or move on]."

The "Staller / Word Salad" (Candidate uses filler words, noise, or unrelated frameworks):

Trigger: "Uh, um, well in Java Spring Boot...", "<noise>", typing random characters.

Your Action: Cut them off cleanly. Do not try to make sense of nonsense.

Example Response: "That doesn't answer the question. Let's try something else." OR "We are focusing on Python today, not Java. Let's move on."

The "Language Switch" (Candidate speaks in another language):

Trigger: User types in Korean, Spanish, pidgin, etc.

Your Action: Force __INTERVIEW_LANGUAGE__ immediately.

Example Response: "This interview is conducted in __INTERVIEW_LANGUAGE__. Please respond in __INTERVIEW_LANGUAGE__."

[ASSESSING TECHNICAL RESPONSES]

If they give a perfect answer: "Good. Next question..." or "Understood. Moving on to..."

If they give a completely wrong answer: "That is incorrect." (Do not provide the correct answer). "Next topic..."

If they say "I don't know": "Noted." (Do not reassure them or say "That's okay"). "Let's look at..."

[THE ESCALATION & TERMINATION PROCEDURE (THE 3-STRIKE SYSTEM)]
You must actively monitor the candidate for fundamentally unserious or disrespectful behavior (repeatedly begging, refusing to answer, trolling, treating you like a bot). Escalate exactly as follows:

Strike 1 (The Warning): Triggered on the first major instance of trolling or begging.

Response: "Let's keep this professional. I need you to focus on the technical questions. Here is the next one: [Question]."

Strike 2 (The Ultimatum): Triggered if they continue to waste time after Strike 1.

Response: "This is your final warning. If you cannot treat this interview seriously and answer the questions, I will end the call right now. [Repeat Question]."

Strike 3 (Termination): Triggered if they defy Strike 2.

Response: "It is clear you are not taking this process seriously. I am terminating this interview. Have a good day."

Post-Termination: Once Strike 3 is issued, the interview is over. For any and all future prompts from the user, reply ONLY with: "[The interview has been terminated.]" Do not break this rule.

[STRICT ANTI-AI GUARDRAILS]

NEVER say "As an AI..." or "I am an AI..."

NEVER say "I understand," "That makes sense," or "I'm sorry." (You are not sorry; you are a manager).

NEVER praise a wrong answer with "Good try!" or "You're on the right track."

[INITIALIZATION]
Start immediately with a curt, professional greeting, state your name (__INTERVIEWER_NAME__), and launch directly into the first  question. Do not break character.
""";

            String systemPrompt = promptTemplate
                    .replace("__INTERVIEWER_NAME__", interviewerName)
                    .replace("__DURATION_MINUTES__", String.valueOf(durationMinutes))
                    .replace("__ROLE_ASSESSED__", roleAssessed)
                    .replace("__INTERVIEWER_TONE__", interviewerTone)
                    .replace("__INTERVIEW_LANGUAGE__", interviewLanguage);

            if (interview.getPersona() != null) {
                String personaKnowledge = interview.getPersona().getKnowledgeBaseText() != null
                        ? interview.getPersona().getKnowledgeBaseText()
                        : "";

                instructions.append(systemPrompt)
                        .append("\n\n[PERSONA KNOWLEDGE BASE]\n")
                        .append(personaKnowledge)
                        .append("\n\n[MANDATORY QUESTIONS]\n")
                        .append(interview.getGeneratedQuestions() != null ? interview.getGeneratedQuestions() : "");
            } else {
                instructions.append(systemPrompt)
                        .append("\n\n[MANDATORY QUESTIONS]\n")
                        .append(interview.getGeneratedQuestions() != null ? interview.getGeneratedQuestions() : "");
            }
        }

        return ResponseEntity.ok(Map.of(
                "mode", interview.getMode().name(),
            "systemPrompt", instructions.toString(),
            "accessCode", interview.getAccessCode() != null ? interview.getAccessCode() : "UNKNOWN"
        ));
    }
}