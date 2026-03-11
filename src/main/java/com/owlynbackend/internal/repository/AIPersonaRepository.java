package com.owlynbackend.internal.repository;



import com.owlynbackend.internal.model.AIPersona;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AIPersonaRepository extends JpaRepository<AIPersona, UUID> {
    List<AIPersona> findByWorkspaceId(UUID workspaceId);
    // Add this to securely find a persona ensuring it belongs to their workspace
    Optional<AIPersona> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
}