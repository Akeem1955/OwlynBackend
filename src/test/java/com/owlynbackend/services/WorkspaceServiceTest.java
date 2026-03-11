//package com.owlynbackend.services;
//
//
//import com.owlynbackend.internal.dto.WorkspaceDTOs.InviteReq;
//import com.owlynbackend.internal.model.User;
//import com.owlynbackend.internal.model.Workspace;
//import com.owlynbackend.internal.model.WorkspaceMember;
//import com.owlynbackend.internal.model.enums.Role;
//import com.owlynbackend.internal.repository.UserRepository;
//import com.owlynbackend.internal.repository.WorkspaceMemberRepository;
//import com.owlynbackend.internal.repository.WorkspaceRepository;
//import lombok.extern.slf4j.Slf4j;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.ArgumentCaptor;
//import org.mockito.Captor;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//import org.springframework.security.crypto.password.PasswordEncoder;
//
//import java.util.Optional;
//import java.util.UUID;
//
//import static org.junit.jupiter.api.Assertions.assertEquals;
//import static org.junit.jupiter.api.Assertions.assertThrows;
//import static org.junit.jupiter.api.Assertions.assertTrue;
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.ArgumentMatchers.anyString;
//import static org.mockito.Mockito.*;
//
//@ExtendWith(MockitoExtension.class)
//@Slf4j
//public class WorkspaceServiceTest {
//
//    @Mock private WorkspaceRepository workspaceRepository;
//    @Mock private WorkspaceMemberRepository workspaceMemberRepository;
//    @Mock private UserRepository userRepository;
//    @Mock private PasswordEncoder passwordEncoder;
//
//    @Captor
//    private ArgumentCaptor<User> userCaptor;
//
//    @Captor
//    private ArgumentCaptor<WorkspaceMember> workspaceMemberCaptor;
//
//    @InjectMocks
//    private WorkspaceService workspaceService;
//
//    private User admin;
//    private Workspace workspace;
//
//    @BeforeEach
//    void setUp() {
//        log.info("Setting up WorkspaceServiceTest dependencies...");
//        admin = User.builder().id(UUID.randomUUID()).role(Role.ADMIN).build();
//        workspace = Workspace.builder().id(UUID.randomUUID()).name("TechCorp").build();
//    }
//
//    @Test
//    void inviteRecruiter_Success_GeneratesTempPasswordAndSaves() {
//        log.info("Starting test: inviteRecruiter_Success_GeneratesTempPasswordAndSaves");
//        // Arrange
//        InviteReq req = new InviteReq();
//        req.setEmail("amina@techcorp.com");
//        req.setFullName("Amina");
//        log.debug("Arranged InviteReq: {}", req.getEmail());
//
//        WorkspaceMember adminMember = WorkspaceMember.builder().workspace(workspace).build();
//
//        when(workspaceMemberRepository.findByUserId(admin.getId())).thenReturn(Optional.of(adminMember));
//        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty()); // Email is free
//        when(passwordEncoder.encode(anyString())).thenReturn("hashed-temp-password");
//
//        log.info("Acting: workspaceService.inviteRecruiter()");
//        // Act
//        String responseMessage = workspaceService.inviteRecruiter(admin, req);
//
//        log.info("Asserting: Verifying that temporary password was generated and User/Member were saved");
//        // Assert
//        assertTrue(responseMessage.contains("Temporary password:"));
//
//        verify(userRepository, times(1)).save(userCaptor.capture());
//        User savedUser = userCaptor.getValue();
//        assertEquals("amina@techcorp.com", savedUser.getEmail());
//        assertEquals("Amina", savedUser.getFullName());
//        assertEquals("hashed-temp-password", savedUser.getPassword());
//        assertEquals(Role.RECRUITER, savedUser.getRole());
//
//        verify(workspaceMemberRepository, times(1)).save(workspaceMemberCaptor.capture());
//        WorkspaceMember savedMember = workspaceMemberCaptor.getValue();
//        assertEquals(workspace.getId(), savedMember.getWorkspace().getId());
//        assertEquals(savedUser.getId(), savedMember.getUser().getId());
//        assertEquals(Role.RECRUITER, savedMember.getRole());
//        log.info("Test passed: inviteRecruiter_Success_GeneratesTempPasswordAndSaves");
//    }
//
//    @Test
//    void inviteRecruiter_Fails_IfUserAlreadyExists() {
//        log.info("Starting test: inviteRecruiter_Fails_IfUserAlreadyExists");
//        // Arrange
//        InviteReq req = new InviteReq();
//        req.setEmail("amina@techcorp.com");
//        log.debug("Arranged existing user email: {}", req.getEmail());
//
//        WorkspaceMember adminMember = WorkspaceMember.builder().workspace(workspace).build();
//        when(workspaceMemberRepository.findByUserId(admin.getId())).thenReturn(Optional.of(adminMember));
//        when(userRepository.findByEmail("amina@techcorp.com")).thenReturn(Optional.of(new User())); // Email taken
//
//        log.info("Acting & Asserting: Expecting failure due to duplicate email");
//        // Act & Assert
//        RuntimeException ex = assertThrows(RuntimeException.class, () -> workspaceService.inviteRecruiter(admin, req));
//        assertEquals("User with this email already exists.", ex.getMessage());
//        verify(userRepository, never()).save(any(User.class)); // Safety check: ensures DB wasn't touched
//        log.info("Test passed: inviteRecruiter_Fails_IfUserAlreadyExists");
//    }
//
//    @Test
//    void inviteRecruiter_Fails_IfAdminNotInWorkspace() {
//        log.info("Starting test: inviteRecruiter_Fails_IfAdminNotInWorkspace");
//        // Arrange
//        InviteReq req = new InviteReq();
//        req.setEmail("amina@techcorp.com");
//        log.debug("Arranged admin with no workspace alignment");
//
//        when(workspaceMemberRepository.findByUserId(admin.getId())).thenReturn(Optional.empty());
//
//        log.info("Acting & Asserting: Expecting failure because admin has no workspace");
//        // Act & Assert
//        RuntimeException ex = assertThrows(RuntimeException.class, () -> workspaceService.inviteRecruiter(admin, req));
//        assertEquals("Workspace not found for user.", ex.getMessage());
//        verify(userRepository, never()).save(any(User.class));
//        log.info("Test passed: inviteRecruiter_Fails_IfAdminNotInWorkspace");
//    }
//
//    @Test
//    void inviteRecruiter_Fails_IfPayloadIsMalformed() {
//        log.info("Starting test: inviteRecruiter_Fails_IfPayloadIsMalformed");
//        // Arrange
//        InviteReq req = new InviteReq();
//        req.setEmail(null); // Malformed payload (missing email)
//        log.debug("Arranged malformed payload (null email)");
//
//        WorkspaceMember adminMember = WorkspaceMember.builder().workspace(workspace).build();
//        // Assume failure happens before hitting repository queries if validation is correct
//        lenient().when(workspaceMemberRepository.findByUserId(admin.getId())).thenReturn(Optional.of(adminMember));
//
//        log.info("Acting & Asserting: Expecting validation failure for malformed payload");
//        // Act & Assert - Expect validation failure
//        assertThrows(Exception.class, () -> workspaceService.inviteRecruiter(admin, req));
//        verify(userRepository, never()).save(any(User.class));
//        log.info("Test passed: inviteRecruiter_Fails_IfPayloadIsMalformed");
//    }
//}