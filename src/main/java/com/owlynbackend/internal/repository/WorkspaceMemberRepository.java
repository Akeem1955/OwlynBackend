package com.owlynbackend.internal.repository;


import com.owlynbackend.internal.model.WorkspaceMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, String> {
    Optional<WorkspaceMember> findByUserId(UUID id);


    List<WorkspaceMember> findByWorkspaceId(UUID id);

    Optional<WorkspaceMember> findByWorkspaceIdAndUserId(UUID id, UUID targetUserId);
}
