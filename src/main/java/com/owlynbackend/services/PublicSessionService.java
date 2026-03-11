package com.owlynbackend.services;

import com.owlynbackend.config.security.JwtManager;
import com.owlynbackend.internal.dto.InterviewDTOs.GenerateQuestionsReq;
import com.owlynbackend.internal.dto.InterviewDTOs.GenerateQuestionsRes;
import com.owlynbackend.internal.dto.PublicSessionDTOs.GeneratePracticeReq;
import com.owlynbackend.internal.dto.PublicSessionDTOs.PublicSessionRes;
import com.owlynbackend.internal.model.Interview;
import com.owlynbackend.internal.model.User;
import com.owlynbackend.internal.model.Workspace;
import com.owlynbackend.internal.model.WorkspaceMember;
import com.owlynbackend.internal.model.enums.InterviewMode;
import com.owlynbackend.internal.model.enums.InterviewStatus;
import com.owlynbackend.internal.model.enums.Role;
import com.owlynbackend.internal.repository.InterviewRepository;
import com.owlynbackend.internal.repository.UserRepository;
import com.owlynbackend.internal.repository.WorkspaceMemberRepository;
import com.owlynbackend.internal.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PublicSessionService {

    private final InterviewRepository interviewRepository;
    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final GeminiAgentService geminiAgentService;
    private final JwtManager jwtManager;
    private final PasswordEncoder passwordEncoder;
    private final LiveKitTokenService liveKitTokenService;
    private final SecureRandom secureRandom = new SecureRandom();

    // The Hidden System Workspace for B2C Users
    private Workspace getOrCreateSystemWorkspace() {
        return userRepository.findByEmail("system@owlyn.com")
                .flatMap(user -> workspaceMemberRepository.findByUserId(user.getId()).map(WorkspaceMember::getWorkspace))
                .orElseGet(() -> {
                    User sysUser = User.builder()
                            .email("system@owlyn.com")
                            .fullName("Owlyn AI System")
                            .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                            .role(Role.ADMIN)
                            .build();
                    userRepository.save(sysUser);

                    Workspace ws = Workspace.builder()
                            .name("Owlyn Public Environment")
                            .owner(sysUser)
                            .build();
                    workspaceRepository.save(ws);

                    WorkspaceMember member = WorkspaceMember.builder()
                            .id(new WorkspaceMember.WorkspaceMemberId(ws.getId(), sysUser.getId()))
                            .workspace(ws)
                            .user(sysUser)
                            .role(Role.ADMIN)
                            .build();
                    workspaceMemberRepository.save(member);

                    return ws;
                });
    }

    private String generateUniqueAccessCode() {
        String code;
        boolean isUnique;
        do {
            code = String.valueOf(100000 + secureRandom.nextInt(900000));
            isUnique = !interviewRepository.existsByAccessCodeAndStatusIn(
                    code, List.of(InterviewStatus.UPCOMING, InterviewStatus.ACTIVE)
            );
        } while (!isUnique);
        return code;
    }

    @Transactional
    public PublicSessionRes createPracticeSession(GeneratePracticeReq req) {
        Workspace systemWorkspace = getOrCreateSystemWorkspace();
        String accessCode = generateUniqueAccessCode();

        // 1. Call Agent 1 to auto-generate the mock questions!
        GenerateQuestionsReq aiReq = new GenerateQuestionsReq(
                req.getTopic(),
                "Make the difficulty " + req.getDifficulty() + ". Act as a strict FAANG interviewer.",
                3
        );
        GenerateQuestionsRes aiRes = geminiAgentService.draftQuestions(aiReq);

        // 2. Create the Session
        Interview interview = Interview.builder()
                .workspace(systemWorkspace)
                .createdBy(systemWorkspace.getOwner())
                .title("Practice: " + req.getTopic())
                .accessCode(accessCode)
                .durationMinutes(req.getDurationMinutes() != null ? req.getDurationMinutes() : 30)
                .toolsEnabled(Map.of("codeEditor", true, "whiteboard", true))
                .aiInstructions("Strict FAANG Mock Interviewer")
                .generatedQuestions(aiRes.getDraftedQuestions())
                .mode(InterviewMode.PRACTICE)
                // Set to ACTIVE immediately because the user is instantly dropped into the room
                .status(InterviewStatus.ACTIVE)
                .build();

        interview = interviewRepository.save(interview);

        // 3. Issue the Guest JWT
        String token = jwtManager.generateCandidateToken(accessCode, interview.getId().toString());
        String lkToken = liveKitTokenService.generateCandidateToken(interview.getId().toString(), accessCode);


        return PublicSessionRes.builder()
                .token(token)
                .livekitToken(lkToken)
                .interviewId(interview.getId())
                .accessCode(accessCode)
                .mode(InterviewMode.PRACTICE.name())
                .build();
    }

    @Transactional
    public PublicSessionRes createTutorSession() {
        Workspace systemWorkspace = getOrCreateSystemWorkspace();
        String accessCode = generateUniqueAccessCode();

        // 1. Tutor Mode needs NO auto-generated questions.
        Interview interview = Interview.builder()
                .workspace(systemWorkspace)
                .createdBy(systemWorkspace.getOwner())
                .title("Desktop AI Tutor")
                .accessCode(accessCode)
                .durationMinutes(60)
                .toolsEnabled(Map.of("screenShare", true))
                .aiInstructions("You are a friendly, patient Desktop AI Tutor. Guide the user step-by-step.")
                .generatedQuestions("None. Answer the user's questions based on what is on their screen.")
                .mode(InterviewMode.TUTOR)
                .status(InterviewStatus.ACTIVE)
                .build();

        interview = interviewRepository.save(interview);

        String token = jwtManager.generateCandidateToken(accessCode, interview.getId().toString());
        String lkToken = liveKitTokenService.generateCandidateToken(interview.getId().toString(), accessCode);

        return PublicSessionRes.builder()
                .token(token)
                .livekitToken(lkToken)
                .interviewId(interview.getId())
                .accessCode(accessCode)
                .mode(InterviewMode.TUTOR.name())
                .build();
    }
}