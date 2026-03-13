package com.owlynbackend.services;



import com.owlynbackend.config.security.JwtManager;
import com.owlynbackend.internal.dto.CandidateDTOs;
import com.owlynbackend.internal.dto.InterviewConfigDTOs.CreateInterviewReq;
import com.owlynbackend.internal.dto.InterviewConfigDTOs.InterviewCreatedRes;
import com.owlynbackend.internal.errors.*;
import com.owlynbackend.internal.model.*;
import com.owlynbackend.internal.model.enums.InterviewMode;
import com.owlynbackend.internal.model.enums.InterviewStatus;
import com.owlynbackend.internal.repository.AIPersonaRepository;
import com.owlynbackend.internal.repository.InterviewRepository;
import com.owlynbackend.internal.repository.UserRepository;
import com.owlynbackend.internal.repository.WorkspaceMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InterviewService {

    private final InterviewRepository interviewRepository;
    private final JwtManager jwtManager;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final SecureRandom secureRandom = new SecureRandom();
    private final AIPersonaRepository aiPersonaRepository;
    private final UserRepository userRepository; // Add to injections
    // Inject LiveKitTokenService via constructor
    private final LiveKitTokenService liveKitTokenService;

    // 1. Add this helper method
    private User getAuthenticatedUser(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new UserNotFoundException("Authenticated user not found."));
    }

    // Helper to get the user's workspace
    private Workspace getUserWorkspace(User user) {
        WorkspaceMember member = workspaceMemberRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("User does not belong to any workspace."));
        return member.getWorkspace();
    }

    // The Code Generator (6 Digits)
    private String generateUniqueAccessCode() {
        String code;
        boolean isUnique;
        do {
            int num = 100000 + secureRandom.nextInt(900000); // Guarantees 6 digits (100000 - 999999)
            code = String.valueOf(num);

            // Only check active/upcoming interviews. We can reuse codes from old, completed interviews!
            isUnique = !interviewRepository.existsByAccessCodeAndStatusIn(
                    code, List.of(InterviewStatus.UPCOMING, InterviewStatus.ACTIVE)
            );
        } while (!isUnique);

        return code;
    }

    private Map<String, Boolean> normalizeToolsEnabled(Map<String, Boolean> toolsEnabled) {
        Map<String, Boolean> normalized = new HashMap<>();
        normalized.put("codeEditor", true);
        normalized.put("whiteboard", true);
        normalized.put("notes", true);

        if (toolsEnabled != null) {
            normalized.putAll(toolsEnabled);
        }

        return normalized;
    }

    @Transactional
    public InterviewCreatedRes createInterview(UserDetails creatorDetails, CreateInterviewReq req) {
        User creator = getAuthenticatedUser(creatorDetails);
        Workspace workspace = getUserWorkspace(creator);
        String accessCode = generateUniqueAccessCode();

        AIPersona selectedPersona = null;
        if (req.getPersonaId() != null) {
            selectedPersona = aiPersonaRepository.findById(req.getPersonaId())
                    .orElseThrow(() -> new IllegalArgumentException("Selected AI Persona not found."));
        }

        Interview interview = Interview.builder()
                .workspace(workspace)
                .createdBy(creator)
                .title(req.getTitle())
            .candidateName(req.getCandidateName())
            .candidateEmail(req.getCandidateEmail())
                .accessCode(accessCode)
                .durationMinutes(req.getDurationMinutes() != null ? req.getDurationMinutes() : 45)
            .toolsEnabled(normalizeToolsEnabled(req.getToolsEnabled()))
                .aiInstructions(req.getAiInstructions())
                .generatedQuestions(req.getGeneratedQuestions()) // The Gemini 3.0 Flash questions!
                .persona(selectedPersona)
                .mode(InterviewMode.STANDARD)
                .status(InterviewStatus.UPCOMING)
                .build();

        interview = interviewRepository.save(interview);

        return InterviewCreatedRes.builder()
                .interviewId(interview.getId())
                .title(interview.getTitle())
                .accessCode(interview.getAccessCode())
                .status(interview.getStatus().name())
                .toolsEnabled(interview.getToolsEnabled())
                .build();
    }

    @Transactional(readOnly = true)
    public List<InterviewCreatedRes> getAllWorkspaceInterviews(UserDetails userDetails) {
        User user = getAuthenticatedUser(userDetails);
        Workspace workspace = getUserWorkspace(user);

        return interviewRepository.findByWorkspaceId(workspace.getId())
                .stream()
                .map(i -> InterviewCreatedRes.builder()
                        .interviewId(i.getId())
                        .title(i.getTitle())
                        .accessCode(i.getAccessCode())
                        .status(i.getStatus().name())
                .candidateName(i.getCandidateName())
                .candidateEmail(i.getCandidateEmail())
                .mode(i.getMode() != null ? i.getMode().name() : null)
                        .build())
                .collect(Collectors.toList());
    }

        @Transactional(readOnly = true)
        public InterviewCreatedRes getWorkspaceInterviewById(UserDetails userDetails, UUID interviewId) {
        User user = getAuthenticatedUser(userDetails);
        Workspace workspace = getUserWorkspace(user);

        Interview interview = interviewRepository.findById(interviewId)
            .orElseThrow(() -> new InvalidRequestException("Interview not found."));

        if (!interview.getWorkspace().getId().equals(workspace.getId())) {
            throw new WorkspaceAccessDeniedException("You do not have access to this interview.");
        }

        return InterviewCreatedRes.builder()
            .interviewId(interview.getId())
            .title(interview.getTitle())
            .accessCode(interview.getAccessCode())
            .status(interview.getStatus().name())
            .candidateName(interview.getCandidateName())
            .candidateEmail(interview.getCandidateEmail())
            .mode(interview.getMode() != null ? interview.getMode().name() : null)
            .toolsEnabled(normalizeToolsEnabled(interview.getToolsEnabled()))
            .build();
        }

    // Add this to InterviewService.java



    @Transactional(readOnly = true)
    public CandidateDTOs.ValidateCodeRes validateAccessCode(String accessCode) {
        if (accessCode == null || !accessCode.matches("^\\d{6}$")) {
            throw new InvalidAccessCodeException("Access code must be exactly 6 digits.");
        }

        Interview interview = interviewRepository.findByAccessCode(accessCode);
        if (interview == null || interview.getStatus() != InterviewStatus.UPCOMING) {
            throw new InvalidAccessCodeException("Invalid or expired access code.");
        }

        String guestToken = jwtManager.generateCandidateToken(accessCode, interview.getId().toString());

        // NEW: Generate LiveKit Token
        String lkToken = liveKitTokenService.generateCandidateToken(interview.getId().toString(), accessCode);
        Map<String, Boolean> toolsEnabled = normalizeToolsEnabled(interview.getToolsEnabled());

        return CandidateDTOs.ValidateCodeRes.builder()
                .token(guestToken)
                .livekitToken(lkToken) // Hand it to frontend!
            .candidateName(interview.getCandidateName())
            .personaName(interview.getPersona() != null ? interview.getPersona().getName() : "Owlyn")
                .interviewId(interview.getId())
                .title(interview.getTitle())
                .durationMinutes(interview.getDurationMinutes())
            .toolsEnabled(toolsEnabled)
            .config(CandidateDTOs.ValidateCodeConfigRes.builder()
                .toolsEnabled(toolsEnabled)
                .build())
                .build();
    }

    @Transactional
    public void startInterviewLockdown(String accessCode) {
        Interview interview = interviewRepository.findByAccessCode(accessCode);

        if (interview == null || interview.getStatus() != InterviewStatus.UPCOMING) {
            throw new InterviewAlreadyActiveException("Cannot start interview. Code is invalid or already active.");
        }

        // Lock the room so nobody else can use this code
        interview.setStatus(InterviewStatus.ACTIVE);
        interviewRepository.save(interview);
    }

    @Transactional
    public void completeInterviewByAccessCode(String accessCode) {
        Interview interview = interviewRepository.findByAccessCode(accessCode);

        if (interview == null) {
            throw new InvalidAccessCodeException("Interview not found for the provided code.");
        }

        if (interview.getStatus() == InterviewStatus.COMPLETED) {
            return;
        }
    }

    @Transactional
    public void completeInterviewById(UUID interviewId) {
        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new InvalidRequestException("Interview not found."));

        if (interview.getStatus() == InterviewStatus.COMPLETED) {
            return;
        }
    }

    @Transactional(readOnly = true)
    public String getRecruiterMonitorToken(org.springframework.security.core.userdetails.UserDetails userDetails, java.util.UUID interviewId) {
        // 1. Fetch User and Interview
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new UserNotFoundException("Authenticated user not found."));

        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new InvalidRequestException("Interview not found."));

        // 2. Validate Workspace Access (Security Gate)
        WorkspaceMember member = workspaceMemberRepository.findByUserId(user.getId())
                .orElseThrow(() -> new WorkspaceAccessDeniedException("User does not belong to a workspace."));

        if (!member.getWorkspace().getId().equals(interview.getWorkspace().getId())) {
            throw new WorkspaceAccessDeniedException("You do not have access to this interview's live feed.");
        }

        // 3. Generate the token
        return liveKitTokenService.generateRecruiterMonitorToken(interviewId.toString(), user.getEmail());
    }
}