package com.owlynbackend.services;

import com.owlynbackend.internal.dto.ReportDTOs.AddFeedbackReq;
import com.owlynbackend.internal.dto.ReportDTOs.ReportRes;
import com.owlynbackend.internal.errors.InvalidRequestException;
import com.owlynbackend.internal.errors.UserNotFoundException;
import com.owlynbackend.internal.errors.WorkspaceAccessDeniedException;
import com.owlynbackend.internal.model.Interview;
import com.owlynbackend.internal.model.InterviewReport;
import com.owlynbackend.internal.model.User;
import com.owlynbackend.internal.model.WorkspaceMember;
import com.owlynbackend.internal.model.enums.FinalDecision;
import com.owlynbackend.internal.repository.InterviewReportRepository;
import com.owlynbackend.internal.repository.InterviewRepository;
import com.owlynbackend.internal.repository.UserRepository;
import com.owlynbackend.internal.repository.WorkspaceMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InterviewReportService {

    private final InterviewReportRepository reportRepository;
    private final InterviewRepository interviewRepository;
    private final UserRepository userRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;

    private User getAuthenticatedUser(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new UserNotFoundException("Authenticated user not found."));
    }

    // Security Gate: Prove this recruiter works for the company that owns the interview
    private void validateWorkspaceAccess(User user, Interview interview) {
        WorkspaceMember member = workspaceMemberRepository.findByUserId(user.getId())
                .orElseThrow(() -> new WorkspaceAccessDeniedException("User does not belong to a workspace."));

        if (!member.getWorkspace().getId().equals(interview.getWorkspace().getId())) {
            throw new WorkspaceAccessDeniedException("You do not have access to this interview's report.");
        }
    }

    @Transactional(readOnly = true)
    public ReportRes getReport(UserDetails userDetails, UUID interviewId) {
        User user = getAuthenticatedUser(userDetails);
        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new InvalidRequestException("Interview not found."));

        validateWorkspaceAccess(user, interview);

        InterviewReport report = reportRepository.findByInterviewId(interviewId)
                .orElseThrow(() -> new InvalidRequestException("Report has not been generated yet."));

        return mapToRes(report);
    }

    @Transactional
    public ReportRes addHumanFeedback(UserDetails userDetails, UUID interviewId, AddFeedbackReq req) {
        User user = getAuthenticatedUser(userDetails);
        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new InvalidRequestException("Interview not found."));

        validateWorkspaceAccess(user, interview);

        InterviewReport report = reportRepository.findByInterviewId(interviewId)
                .orElseThrow(() -> new InvalidRequestException("Report has not been generated yet."));

        // Save notes and safely parse the HIRE/DECLINE enum
        if (req.getHumanFeedback() != null) {
            report.setHumanFeedback(req.getHumanFeedback());
        }
        if (req.getDecision() != null && !req.getDecision().isBlank()) {
            try {
                report.setFinalDecision(FinalDecision.valueOf(req.getDecision().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new InvalidRequestException("Decision must be HIRE, DECLINE, or PENDING");
            }
        }
        reportRepository.save(report);

        return mapToRes(report);
    }

    // Strict DTO Mapping
    private ReportRes mapToRes(InterviewReport report) {
        return ReportRes.builder()
                .reportId(report.getId())
                .interviewId(report.getInterview().getId())
                .candidateEmail(report.getCandidateEmail())
                .score(report.getScore())
                .behavioralNotes(report.getBehavioralNotes())
                .codeOutput(report.getCodeOutput())
                .behaviorFlags(report.getBehaviorFlags())
                .humanFeedback(report.getHumanFeedback())
                .finalDecision(report.getFinalDecision().name())
                .build();
    }

    // Add inside InterviewReportService.java

    @Transactional(readOnly = true)
    public List<ReportRes> getAllWorkspaceReports(UserDetails userDetails) {
        User user = getAuthenticatedUser(userDetails);

        WorkspaceMember member = workspaceMemberRepository.findByUserId(user.getId())
                .orElseThrow(() -> new WorkspaceAccessDeniedException("User does not belong to a workspace."));

        UUID workspaceId = member.getWorkspace().getId();

        return reportRepository.findByInterviewWorkspaceId(workspaceId)
                .stream()
                .map(this::mapToRes) // Reusing our existing DTO mapper!
                .collect(Collectors.toList());
    }




    // 2. ADD THIS NEW METHOD:
    @Transactional(readOnly = true)
    public ReportRes getTopPerformer(UserDetails userDetails) {
        User user = getAuthenticatedUser(userDetails);
        WorkspaceMember member = workspaceMemberRepository.findByUserId(user.getId())
                .orElseThrow(() -> new WorkspaceAccessDeniedException("User does not belong to a workspace."));

        InterviewReport topReport = reportRepository.findFirstByInterviewWorkspaceIdOrderByScoreDesc(member.getWorkspace().getId())
                .orElseThrow(() -> new InvalidRequestException("No reports found for this workspace."));

        return mapToRes(topReport);
    }





}