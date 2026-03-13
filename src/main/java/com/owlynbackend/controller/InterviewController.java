package com.owlynbackend.controller;



import com.owlynbackend.internal.dto.CandidateDTOs;
import com.owlynbackend.internal.dto.InterviewConfigDTOs.CreateInterviewReq;
import com.owlynbackend.internal.dto.InterviewConfigDTOs.InterviewCreatedRes;
import com.owlynbackend.internal.dto.InterviewDTOs.GenerateQuestionsReq;
import com.owlynbackend.internal.dto.InterviewDTOs.GenerateQuestionsRes;
import com.owlynbackend.services.GeminiAgentService;
import com.owlynbackend.services.InterviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails; // Add this import
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/interviews")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewService interviewService;
    private final GeminiAgentService geminiAgentService;

    // STEP 1: The Recruiter asks Gemini 3.0 Flash to draft questions (Stateless)
    @PostMapping("/generate-questions")
    public ResponseEntity<GenerateQuestionsRes> generateQuestions(@RequestBody GenerateQuestionsReq req) {
        GenerateQuestionsRes response = geminiAgentService.draftQuestions(req);
        return ResponseEntity.ok(response);
    }

    // STEP 2: The Recruiter approves the questions and generates the 6-Digit Code
    @PostMapping
    public ResponseEntity<InterviewCreatedRes> createInterview(
            @AuthenticationPrincipal UserDetails creatorDetails,
            @RequestBody CreateInterviewReq req) {
        InterviewCreatedRes response = interviewService.createInterview(creatorDetails, req);
        return ResponseEntity.ok(response);
    }

    // DASHBOARD: Fetch all interviews for the workspace
    @GetMapping
    public ResponseEntity<List<InterviewCreatedRes>> getWorkspaceInterviews(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(interviewService.getAllWorkspaceInterviews(userDetails));
    }

    @GetMapping("/{id}")
    public ResponseEntity<InterviewCreatedRes> getWorkspaceInterviewById(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable java.util.UUID id) {
        return ResponseEntity.ok(interviewService.getWorkspaceInterviewById(userDetails, id));
    }



    // STEP 1: Candidate enters the 6-digit code (Public Endpoint)
    @PostMapping("/validate-code")
    public ResponseEntity<CandidateDTOs.ValidateCodeRes> validateCode(@RequestBody CandidateDTOs.ValidateCodeReq req) {
        return ResponseEntity.ok(interviewService.validateAccessCode(req.getCode()));
    }

    // STEP 2: Candidate clicks "Start Interview" (Requires the Guest JWT they just got)
    @PutMapping("/{code}/status/active")
    public ResponseEntity<Map<String, String>> activateInterview(@PathVariable String code) {
        interviewService.startInterviewLockdown(code);
        return ResponseEntity.ok(Map.of("message", "Interview is now ACTIVE. Lockdown initiated."));
    }

    @PutMapping("/{code}/status/completed")
    public ResponseEntity<Map<String, String>> completeInterview(@PathVariable String code) {
        interviewService.completeInterviewByAccessCode(code);
        return ResponseEntity.ok(Map.of("message", "Interview completion requested. Status will be COMPLETED after report generation."));
    }


// Inside InterviewController.java, add this GET mapping:

    @GetMapping("/{id}/monitor-token")
    public ResponseEntity<Map<String, String>> getMonitorToken(
            @AuthenticationPrincipal org.springframework.security.core.userdetails.UserDetails userDetails,
            @PathVariable java.util.UUID id) {

        String lkToken = interviewService.getRecruiterMonitorToken(userDetails, id);
        return ResponseEntity.ok(Map.of("livekitToken", lkToken));
    }

}
