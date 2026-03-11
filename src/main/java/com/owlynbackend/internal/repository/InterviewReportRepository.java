package com.owlynbackend.internal.repository;

import com.owlynbackend.internal.model.InterviewReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InterviewReportRepository extends JpaRepository<InterviewReport, UUID> {
    Optional<InterviewReport> findByInterviewId(UUID interviewId);
    // Add this to fetch all reports for a specific company's workspace
    List<InterviewReport> findByInterviewWorkspaceId(UUID workspaceId);

    // Inside InterviewReportRepository.java

    // Finds the absolute highest scoring candidate for a specific company
    java.util.Optional<InterviewReport> findFirstByInterviewWorkspaceIdOrderByScoreDesc(java.util.UUID workspaceId);
}