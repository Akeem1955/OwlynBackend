package com.owlynbackend.internal.repository;



import com.owlynbackend.internal.model.Interview;
import com.owlynbackend.internal.model.enums.InterviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface InterviewRepository extends JpaRepository<Interview, UUID> {

    // Finds all interviews belonging to the recruiter's team
    List<Interview> findByWorkspaceId(UUID workspaceId);

    // Checks if a 6-digit code is currently being used in a live/upcoming session
    boolean existsByAccessCodeAndStatusIn(String accessCode, Collection<InterviewStatus> statuses);

    // Phase 3 Preview: We will use this when the candidate enters the code
    Interview findByAccessCode(String accessCode);
}