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
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkspaceService {

    private static final Path LOGO_UPLOAD_DIR = Paths.get("uploads", "workspaces");

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
    public WorkspaceInfoRes updateWorkspace(UserDetails adminDetails, String name, MultipartFile logo) {
        User admin = getAuthenticatedAdmin(adminDetails);
        Workspace workspace = getAdminWorkspace(admin);

        if (name != null && !name.isBlank()) {
            workspace.setName(name);
        }

        if (logo != null && !logo.isEmpty()) {
            if (logo.getSize() > 7L * 1024 * 1024) {
                throw new InvalidRequestException("Logo file exceeds max size of 7MB.");
            }

            String contentType = logo.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                throw new InvalidRequestException("Logo must be an image file.");
            }

            try {
                Files.createDirectories(LOGO_UPLOAD_DIR);

                String originalName = logo.getOriginalFilename() != null ? logo.getOriginalFilename() : "logo";
                int dotIndex = originalName.lastIndexOf('.');
                String extension = dotIndex >= 0 ? originalName.substring(dotIndex) : "";

                String fileName = workspace.getId() + "-" + UUID.randomUUID() + extension;
                Path destination = LOGO_UPLOAD_DIR.resolve(fileName).normalize();
                Files.copy(logo.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);

                workspace.setLogoUrl("/uploads/workspaces/" + fileName);
            } catch (Exception e) {
                throw new InvalidRequestException("Failed to process uploaded logo file.");
            }
        }

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