package com.owlynbackend.services;



import com.owlynbackend.internal.dto.PersonaDTOs.CreatePersonaReq;
import com.owlynbackend.internal.dto.PersonaDTOs.PersonaRes;
import com.owlynbackend.internal.errors.InvalidRequestException;
import com.owlynbackend.internal.errors.UserNotFoundException;
import com.owlynbackend.internal.model.AIPersona;
import com.owlynbackend.internal.model.User;
import com.owlynbackend.internal.model.Workspace;
import com.owlynbackend.internal.model.WorkspaceMember;
import com.owlynbackend.internal.repository.AIPersonaRepository;
import com.owlynbackend.internal.repository.UserRepository;
import com.owlynbackend.internal.repository.WorkspaceMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AIPersonaService {

    private final AIPersonaRepository aiPersonaRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final DocumentExtractionService documentExtractionService;
    private final UserRepository userRepository; // Add to injections

    // 1. Add this helper method
    private User getAuthenticatedUser(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new UserNotFoundException("Authenticated user not found."));
    }

    // Helper: Validates user is an ADMIN (or Recruiter, depending on your rules)
    private Workspace getUserWorkspace(User user) {
        WorkspaceMember member = workspaceMemberRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Workspace not found for user."));
        return member.getWorkspace();
    }

    @Transactional
    public PersonaRes createPersona(UserDetails userDetails, CreatePersonaReq req, MultipartFile file) {
        User user = getAuthenticatedUser(userDetails);
        Workspace workspace = getUserWorkspace(user);

        // 1. Extract text from the PDF/DOCX if a file was provided
        String extractedText = null;
        if (file != null && !file.isEmpty()) {
            extractedText = documentExtractionService.extractTextFromFile(file);
        }

        // 2. Build the Entity
        AIPersona persona = AIPersona.builder()
                .workspace(workspace)
                .name(req.getName())
                .roleTitle(req.getRoleTitle())
                .empathyScore(req.getEmpathyScore())
                .analyticalDepth(req.getAnalyticalDepth())
                .directnessScore(req.getDirectnessScore())
                .tone(req.getTone())
                .domainExpertise(req.getDomainExpertise())
                .knowledgeBaseText(extractedText) // The Massive Context Hack!
                .build();

        persona = aiPersonaRepository.save(persona);

        return mapToRes(persona);
    }

    @Transactional(readOnly = true)
    public List<PersonaRes> getWorkspacePersonas(UserDetails userDetails) {
        User user = getAuthenticatedUser(userDetails);
        Workspace workspace = getUserWorkspace(user);

        return aiPersonaRepository.findByWorkspaceId(workspace.getId())
                .stream()
                .map(this::mapToRes)
                .collect(Collectors.toList());
    }

    private PersonaRes mapToRes(AIPersona p) {
        return PersonaRes.builder()
                .id(p.getId())
                .name(p.getName())
                .roleTitle(p.getRoleTitle())
                .empathyScore(p.getEmpathyScore())
                .analyticalDepth(p.getAnalyticalDepth())
                .directnessScore(p.getDirectnessScore())
                .tone(p.getTone())
                .domainExpertise(p.getDomainExpertise())
                .hasKnowledgeBase(p.getKnowledgeBaseText() != null && !p.getKnowledgeBaseText().isBlank())
                .build();
    }




    @Transactional
    public void deletePersona(UserDetails userDetails, UUID personaId) {
        User user = getAuthenticatedUser(userDetails);
        Workspace workspace = getUserWorkspace(user);

        AIPersona persona = aiPersonaRepository.findByIdAndWorkspaceId(personaId, workspace.getId())
                .orElseThrow(() -> new InvalidRequestException("Persona not found or you do not have permission to delete it."));

        try {
            aiPersonaRepository.delete(persona);
            aiPersonaRepository.flush(); // Force DB sync to catch constraint violations immediately
        } catch (DataIntegrityViolationException e) {
            throw new InvalidRequestException("Cannot delete this Persona because it is currently attached to existing interviews.");
        }
    }

}