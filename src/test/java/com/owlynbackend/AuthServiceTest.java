package com.owlynbackend;

import lombok.extern.slf4j.Slf4j;

import com.owlynbackend.config.security.JwtManager;
import com.owlynbackend.internal.dto.AuthResponse;
import com.owlynbackend.internal.dto.PendingAuthDTO;
import com.owlynbackend.internal.dto.UserDto;
import com.owlynbackend.internal.errors.InvalidCredentialsException;
import com.owlynbackend.internal.errors.InvalidOtpException;
import com.owlynbackend.internal.errors.UserAlreadyExistException;
import com.owlynbackend.internal.model.User;
import com.owlynbackend.internal.model.Workspace;
import com.owlynbackend.internal.model.WorkspaceMember;
import com.owlynbackend.internal.model.enums.Role;
import com.owlynbackend.internal.repository.UserRepository;
import com.owlynbackend.internal.repository.WorkspaceMemberRepository;
import com.owlynbackend.internal.repository.WorkspaceRepository;
import com.owlynbackend.services.AuthService;
import com.owlynbackend.services.OtpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Slf4j
public class AuthServiceTest {

    @Mock private RedisTemplate<String, PendingAuthDTO> redisTemplate;
    @Mock private ValueOperations<String, PendingAuthDTO> valueOperations;
    @Mock private OtpService otpService;
    @Mock private UserRepository userRepository;
    @Mock private WorkspaceRepository workspaceRepository;
    @Mock private WorkspaceMemberRepository workspaceMemberRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtManager jwtManager;

    @Captor private ArgumentCaptor<User> userCaptor;
    @Captor private ArgumentCaptor<Workspace> workspaceCaptor;
    @Captor private ArgumentCaptor<WorkspaceMember> workspaceMemberCaptor;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        log.info("Setting up AuthServiceTest dependencies...");
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void initiateSignup_Success_CachesPendingAuth() {
        log.info("Starting test: initiateSignup_Success_CachesPendingAuth");
        User user = User.builder().email("test@owlyn.com").fullName("Test User").password("rawPass").build();
        log.debug("Arranged User: {}", user.getEmail());
        
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.empty());
        when(otpService.sendEmailOtp(user.getEmail())).thenReturn("123456");

        log.info("Acting: authService.initiateSignup()");
        authService.initiateSignup(user);

        log.info("Asserting: Verifying Redis cache interaction");
        verify(valueOperations).set(eq("otp:signup:test@owlyn.com"), any(PendingAuthDTO.class), any());
        log.info("Test passed: initiateSignup_Success_CachesPendingAuth");
    }

    @Test
    void initiateSignup_Fails_IfUserExists() {
        log.info("Starting test: initiateSignup_Fails_IfUserExists");
        User user = User.builder().email("test@owlyn.com").build();
        log.debug("Arranged User with existing email: {}", user.getEmail());
        
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(new User()));

        log.info("Acting & Asserting: Expecting UserAlreadyExistException");
        assertThrows(UserAlreadyExistException.class, () -> authService.initiateSignup(user));
        log.info("Test passed: initiateSignup_Fails_IfUserExists");
    }

    @Test
    void verifySignup_Success_CreatesUserAndWorkspace() {
        log.info("Starting test: verifySignup_Success_CreatesUserAndWorkspace");
        PendingAuthDTO pending = PendingAuthDTO.builder()
                .email("test@owlyn.com").fullName("Test User").rawPassword("rawPass").expectedOtp("123456").build();
        log.debug("Arranged PendingAuthDTO: {}", pending.getEmail());
        
        when(valueOperations.get("otp:signup:test@owlyn.com")).thenReturn(pending);
        when(passwordEncoder.encode("rawPass")).thenReturn("hashedPass");
        
        User savedUser = User.builder().id(UUID.randomUUID()).email("test@owlyn.com").fullName("Test User").role(Role.ADMIN).build();
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        
        Workspace savedWorkspace = Workspace.builder().id(UUID.randomUUID()).name("Test User's Workspace").build();
        when(workspaceRepository.save(any(Workspace.class))).thenReturn(savedWorkspace);
        when(jwtManager.generateToken(any(), any())).thenReturn("mock-token");

        log.info("Acting: authService.verifySignupAndCreateWorkspace()");
        AuthResponse response = authService.verifySignupAndCreateWorkspace("123456", "test@owlyn.com");

        log.info("Asserting: Verifying User, Workspace, and Member creation");
        assertNotNull(response);
        assertEquals("mock-token", response.getToken());
        assertEquals("test@owlyn.com", response.getUser().getEmail());

        verify(userRepository).save(userCaptor.capture());
        log.debug("Captured Saved User Password: {}", userCaptor.getValue().getPassword());
        assertEquals("hashedPass", userCaptor.getValue().getPassword());
        assertEquals(Role.ADMIN, userCaptor.getValue().getRole());

        verify(workspaceRepository).save(workspaceCaptor.capture());
        assertEquals("Test User's Workspace", workspaceCaptor.getValue().getName());

        verify(workspaceMemberRepository).save(workspaceMemberCaptor.capture());
        assertEquals(Role.ADMIN, workspaceMemberCaptor.getValue().getRole());
        log.info("Test passed: verifySignup_Success_CreatesUserAndWorkspace");
    }

    @Test
    void initiateLogin_Success_SendsOtp() {
        log.info("Starting test: initiateLogin_Success_SendsOtp");
        User dbUser = User.builder().email("test@owlyn.com").password("hashedPass").build();
        log.debug("Arranged DB User: {}", dbUser.getEmail());
        
        when(userRepository.findByEmail("test@owlyn.com")).thenReturn(Optional.of(dbUser));
        when(passwordEncoder.matches("rawPass", "hashedPass")).thenReturn(true);
        when(otpService.sendEmailOtp("test@owlyn.com")).thenReturn("123456");

        log.info("Acting: authService.initiateLogin()");
        authService.initiateLogin("test@owlyn.com", "rawPass");

        log.info("Asserting: Verifying Redis login cache interaction");
        verify(valueOperations).set(eq("otp:login:test@owlyn.com"), any(PendingAuthDTO.class), any());
        log.info("Test passed: initiateLogin_Success_SendsOtp");
    }

    @Test
    void initiateLogin_Fails_WrongPassword() {
        log.info("Starting test: initiateLogin_Fails_WrongPassword");
        User dbUser = User.builder().email("test@owlyn.com").password("hashedPass").build();
        
        when(userRepository.findByEmail("test@owlyn.com")).thenReturn(Optional.of(dbUser));
        when(passwordEncoder.matches("wrongPass", "hashedPass")).thenReturn(false);

        log.info("Acting & Asserting: Expecting InvalidCredentialsException");
        assertThrows(InvalidCredentialsException.class, () -> authService.initiateLogin("test@owlyn.com", "wrongPass"));
        log.info("Test passed: initiateLogin_Fails_WrongPassword");
    }

    @Test
    void verifyLogin_Success_ReturnsResponse() {
        log.info("Starting test: verifyLogin_Success_ReturnsResponse");
        PendingAuthDTO pending = PendingAuthDTO.builder().email("test@owlyn.com").expectedOtp("123456").build();
        when(valueOperations.get("otp:login:test@owlyn.com")).thenReturn(pending);
        
        User dbUser = User.builder().id(UUID.randomUUID()).email("test@owlyn.com").role(Role.ADMIN).build();
        when(userRepository.findByEmail("test@owlyn.com")).thenReturn(Optional.of(dbUser));
        
        Workspace workspace = Workspace.builder().id(UUID.randomUUID()).build();
        WorkspaceMember member = WorkspaceMember.builder().workspace(workspace).build();
        when(workspaceMemberRepository.findByUserId(dbUser.getId())).thenReturn(Optional.of(member));
        when(jwtManager.generateToken(any(), any())).thenReturn("mock-token");

        log.info("Acting: authService.verifyLogin()");
        AuthResponse response = authService.verifyLogin("123456", "test@owlyn.com");

        log.info("Asserting: Verifying Login Response and Token");
        assertEquals("mock-token", response.getToken());
        assertEquals("test@owlyn.com", response.getUser().getEmail());
        log.info("Test passed: verifyLogin_Success_ReturnsResponse");
    }

    @Test
    void getMe_Success_ReturnsUserDto() {
        log.info("Starting test: getMe_Success_ReturnsUserDto");
        User dbUser = User.builder().id(UUID.randomUUID()).email("test@owlyn.com").fullName("Test User").role(Role.ADMIN).build();
        when(userRepository.findByEmail("test@owlyn.com")).thenReturn(Optional.of(dbUser));

        log.info("Acting: authService.getMe()");
        UserDto result = authService.getMe("test@owlyn.com");

        log.info("Asserting: Verifying UserDto content");
        assertEquals("test@owlyn.com", result.getEmail());
        assertEquals("Test User", result.getFullName());
        assertEquals(Role.ADMIN, result.getRole());
        log.info("Test passed: getMe_Success_ReturnsUserDto");
    }
}




