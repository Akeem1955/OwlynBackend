package com.owlynbackend.services;

import com.owlynbackend.internal.dto.WorkspaceDTOs.*;
import com.owlynbackend.internal.errors.*;
import com.owlynbackend.internal.model.User;
import com.owlynbackend.internal.model.Workspace;
import com.owlynbackend.internal.model.WorkspaceMember;
import com.owlynbackend.internal.model.enums.Role;
import com.owlynbackend.internal.repository.UserRepository;
import com.owlynbackend.internal.repository.WorkspaceMemberRepository;
import com.owlynbackend.internal.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // Helper 1: Safely extracts the User entity from UserDetails and validates Admin role
    private User getAuthenticatedAdmin(UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new UserNotFoundException("Authenticated user not found."));

        if (user.getRole() != Role.ADMIN) {
            throw new WorkspaceAccessDeniedException("Access Denied: Only Admins can manage the workspace.");
        }
        return user;
    }

    // Helper 2: Returns the Workspace for the validated Admin
    private Workspace getAdminWorkspace(User admin) {
        WorkspaceMember member = workspaceMemberRepository.findByUserId(admin.getId())
                .orElseThrow(() -> new WorkspaceNotFoundException("Workspace not found for user."));
        return member.getWorkspace();
    }

    @Transactional(readOnly = true)
    public WorkspaceInfoRes getWorkspaceDetails(UserDetails adminDetails) {
        User admin = getAuthenticatedAdmin(adminDetails);
        Workspace workspace = getAdminWorkspace(admin);

        List<WorkspaceMember> members = workspaceMemberRepository.findByWorkspaceId(workspace.getId());

        return WorkspaceInfoRes.builder()
                .workspaceId(workspace.getId())
                .name(workspace.getName())
                .logoUrl(workspace.getLogoUrl())
                .memberCount(members.size())
                .build();
    }

    @Transactional
    public WorkspaceInfoRes updateWorkspace(UserDetails adminDetails, WorkspaceUpdateReq req) {
        User admin = getAuthenticatedAdmin(adminDetails);
        Workspace workspace = getAdminWorkspace(admin);

        if (req.getName() != null && !req.getName().isBlank()) workspace.setName(req.getName());
        if (req.getLogoUrl() != null) workspace.setLogoUrl(req.getLogoUrl());

        workspaceRepository.save(workspace);

        // We can just pass the UserDetails back into getWorkspaceDetails to avoid duplicating the fetch logic
        return getWorkspaceDetails(adminDetails);
    }

    @Transactional
    public String inviteRecruiter(UserDetails adminDetails, InviteReq req) {
        User admin = getAuthenticatedAdmin(adminDetails);
        Workspace workspace = getAdminWorkspace(admin);

        if (req.getEmail() == null || req.getEmail().isBlank()) {
            throw new InvalidRequestException("Email is required.");
        }

        if (userRepository.findByEmail(req.getEmail()).isPresent()) {
            throw new UserAlreadyExistException("User with this email already exists.");
        }

        // Hackathon trick: Generate a temporary 8-char password
        String tempPassword = UUID.randomUUID().toString().substring(0, 8);

        // 1. Create Recruiter User
        User newRecruiter = User.builder()
                .email(req.getEmail())
                .fullName(req.getFullName() != null ? req.getFullName() : "Recruiter")
                .password(passwordEncoder.encode(tempPassword))
                .role(Role.RECRUITER)
                .build();
        userRepository.save(newRecruiter);

        // 2. Add to Workspace
        WorkspaceMember newMember = WorkspaceMember.builder()
                .id(new WorkspaceMember.WorkspaceMemberId(workspace.getId(), newRecruiter.getId()))
                .workspace(workspace)
                .user(newRecruiter)
                .role(Role.RECRUITER)
                .build();
        workspaceMemberRepository.save(newMember);

        // No manual log.info()! AOP handles execution tracking automatically.
        return "Invite successful! Temporary password: " + tempPassword;
    }

    @Transactional(readOnly = true)
    public List<MemberRes> getTeamMembers(UserDetails adminDetails) {
        User admin = getAuthenticatedAdmin(adminDetails);
        Workspace workspace = getAdminWorkspace(admin);

        return workspaceMemberRepository.findByWorkspaceId(workspace.getId())
                .stream()
                .map(wm -> MemberRes.builder()
                        .userId(wm.getUser().getId())
                        .fullName(wm.getUser().getFullName())
                        .email(wm.getUser().getEmail())
                        .role(wm.getRole().name())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public void removeMember(UserDetails adminDetails, UUID targetUserId) {
        User admin = getAuthenticatedAdmin(adminDetails);
        Workspace workspace = getAdminWorkspace(admin);

        if (admin.getId().equals(targetUserId)) {
            throw new InvalidRequestException("Admins cannot remove themselves.");
        }

        WorkspaceMember targetMember = workspaceMemberRepository
                .findByWorkspaceIdAndUserId(workspace.getId(), targetUserId)
                .orElseThrow(() -> new WorkspaceMemberNotFoundException("User is not a member of this workspace."));

        workspaceMemberRepository.delete(targetMember);
        userRepository.delete(targetMember.getUser()); // Hard delete user to keep DB clean for hackathon
    }
}


//remove,get,invite,,update,getadmin