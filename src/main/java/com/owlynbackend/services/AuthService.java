package com.owlynbackend.services;


import com.owlynbackend.config.security.JwtManager;
import com.owlynbackend.internal.dto.AuthResponse;
import com.owlynbackend.internal.dto.PendingAuthDTO;
import com.owlynbackend.internal.errors.InvalidCredentialsException;
import com.owlynbackend.internal.errors.InvalidOtpException;
import com.owlynbackend.internal.errors.UserAlreadyExistException;
import com.owlynbackend.internal.dto.UserDto;
import com.owlynbackend.internal.model.User;
import com.owlynbackend.internal.model.Workspace;
import com.owlynbackend.internal.model.WorkspaceMember;
import com.owlynbackend.internal.model.enums.Role;
import com.owlynbackend.internal.repository.UserRepository;
import com.owlynbackend.internal.repository.WorkspaceMemberRepository;
import com.owlynbackend.internal.repository.WorkspaceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Service
@Slf4j
public class AuthService {

    private final RedisTemplate<String, PendingAuthDTO> redisTemplate;
    private final OtpService otpService;
    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtManager jwtManager;

    @Autowired
    public AuthService(RedisTemplate<String, PendingAuthDTO> redisTemplate, OtpService otpService,
                       UserRepository userRepository, WorkspaceRepository workspaceRepository,
                       WorkspaceMemberRepository workspaceMemberRepository,
                       PasswordEncoder passwordEncoder, JwtManager jwtManager) {
        this.redisTemplate = redisTemplate;
        this.otpService = otpService;
        this.userRepository = userRepository;
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtManager = jwtManager;
    }



    public void initiateSignup(User user) {
        log.info("Initiating signup for email: {}", user.getEmail());
        if (userRepository.findByEmail(user.getEmail()).isPresent()) {
            log.warn("Signup failed: User with email {} already exists", user.getEmail());
            throw new UserAlreadyExistException("User with email " + user.getEmail() + " already exists");
        }

        log.info("Generating OTP for email: {}", user.getEmail());
        String otp = otpService.sendEmailOtp(user.getEmail());
        log.info("OTP generated and sent for email: {}", user.getEmail());

        PendingAuthDTO pendingAuth = PendingAuthDTO.builder()
                .email(user.getEmail())
                .fullName(user.getFullName())
                .rawPassword(user.getPassword())
                .expectedOtp(otp)
                .build();

        String redisKey = "otp:signup:" + user.getEmail();
        log.debug("Storing pending signup in Redis with key: {}", redisKey);
        redisTemplate.opsForValue().set(redisKey, pendingAuth, Duration.ofMinutes(5));
        log.info("Signup request stored in Redis for email: {}", user.getEmail());
    }



    @Transactional
    public AuthResponse verifySignupAndCreateWorkspace(String otp, String email) {
        log.info("Verifying signup OTP for email: {}", email);
        String redisKey = "otp:signup:" + email;
        log.debug("Fetching pending signup from Redis with key: {}", redisKey);
        PendingAuthDTO pendingAuth = redisTemplate.opsForValue().get(redisKey);

        if (pendingAuth == null) {
            log.warn("Signup verification failed for email {}: No pending signup found in Redis", email);
            throw new InvalidOtpException("Invalid or expired OTP");
        }

        if (!pendingAuth.getExpectedOtp().equals(otp)) {
            log.warn("Signup verification failed for email {}: OTP mismatch", email);
            throw new InvalidOtpException("Invalid or expired OTP");
        }

        // 1. Create a real User entity from DTO & Hash Password
        log.debug("Creating user entity for email: {}", email);
        User newUser = User.builder()
                .email(pendingAuth.getEmail())
                .fullName(pendingAuth.getFullName())
                .password(passwordEncoder.encode(pendingAuth.getRawPassword()))
                .role(Role.ADMIN) // Default role as requested
                .build();
        User savedUser = userRepository.save(newUser);
        log.info("User {} created successfully with ID: {}", email, savedUser.getId());

        // 2. Automatically create Workspace
        log.debug("Creating default workspace for user: {}", email);
        Workspace workspace = Workspace.builder()
                .name(savedUser.getFullName() + "'s Workspace")
                .owner(savedUser)
                .build();
        workspace = workspaceRepository.save(workspace);
        log.info("Workspace '{}' created with ID: {} for user: {}", workspace.getName(), workspace.getId(), email);

        // 3. Link user to workspace as ADMIN
        log.debug("Linking user {} to workspace {} as ADMIN", email, workspace.getId());
        WorkspaceMember member = WorkspaceMember.builder()
                .id(new WorkspaceMember.WorkspaceMemberId(workspace.getId(), savedUser.getId()))
                .workspace(workspace)
                .user(savedUser)
                .role(Role.ADMIN)
                .build();
        workspaceMemberRepository.save(member);
        log.debug("User {} linked to workspace {} as ADMIN successfully", email, workspace.getId());

        // Clean up Redis
        log.debug("Deleting signup key from Redis: {}", redisKey);
        redisTemplate.delete(redisKey);
        log.debug("Redis cleanup successful for signup email: {}", email);

        // Generate Token
        log.debug("Generating JWT token for user: {}", email);
        String token = jwtManager.generateToken(savedUser, workspace.getId());
        log.info("JWT token generated for newly signed up user: {}", email);
        UserDto userDto = UserDto.builder()
                .id(savedUser.getId())
                .email(savedUser.getEmail())
                .fullName(savedUser.getFullName())
                .role(savedUser.getRole())
                .build();
        return new AuthResponse(token, userDto);
    }














    public void initiateLogin(String email, String rawPassword) {
        log.info("Initiating login for email: {}", email);
        User dbUser = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("Login failed: User not found with email {}", email);
                    return new InvalidCredentialsException("Invalid credentials");
                });

        log.debug("Verifying password for user: {}", email);
        if (passwordEncoder.matches(rawPassword, dbUser.getPassword())) {
            log.debug("Password match successful for user: {}", email);
            String otp = otpService.sendEmailOtp(dbUser.getEmail());
            log.debug("OTP generated and sent for login: {}", email);

            // For login, we don't need the password in the cache, just the email and expected OTP
            PendingAuthDTO pendingAuth = PendingAuthDTO.builder()
                    .email(dbUser.getEmail())
                    .expectedOtp(otp)
                    .build();

            String redisKey = "otp:login:" + dbUser.getEmail();
            log.debug("Storing pending login in Redis with key: {}", redisKey);
            redisTemplate.opsForValue().set(redisKey, pendingAuth, Duration.ofMinutes(5));
            log.info("Login request stored in Redis for email: {}", email);
            return;
        }
        log.warn("Login failed: Incorrect password for email {}", email);
        throw new InvalidCredentialsException("Invalid credentials");
    }




    @Transactional(readOnly = true)
    public AuthResponse verifyLogin(String otp, String email) {
        log.info("Verifying login OTP for email: {}", email);
        String redisKey = "otp:login:" + email;
        log.debug("Fetching pending login from Redis with key: {}", redisKey);
        PendingAuthDTO pendingAuth = redisTemplate.opsForValue().get(redisKey);

        if (pendingAuth == null) {
            log.warn("Login verification failed for email {}: No pending login found in Redis", email);
            throw new InvalidOtpException("Invalid or expired OTP");
        }

        if (!pendingAuth.getExpectedOtp().equals(otp)) {
            log.warn("Login verification failed for email {}: OTP mismatch", email);
            throw new InvalidOtpException("Invalid or expired OTP");
        }

        // FIX: Safe unwrapping of Optional
        log.debug("Fetching user from database for login: {}", email);
        User dbUser = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.error("Login verification failed: User {} no longer exists in database", email);
                    return new RuntimeException("User no longer exists in database");
                });

        log.debug("Fetching workspace association for user ID: {}", dbUser.getId());
        WorkspaceMember member = workspaceMemberRepository.findByUserId(dbUser.getId())
                .stream().findFirst().orElse(null);

        java.util.UUID workspaceId = (member != null) ? member.getWorkspace().getId() : null;
        if (workspaceId != null) {
            log.debug("User {} associated with workspace ID: {}", email, workspaceId);
        } else {
            log.info("User {} has no associated workspace", email);
        }

        // Clean up Redis
        log.debug("Deleting login key from Redis: {}", redisKey);
        redisTemplate.delete(redisKey);
        log.debug("Redis cleanup successful for login email: {}", email);

        log.debug("Generating JWT token for user: {}", email);
        String token = jwtManager.generateToken(dbUser, workspaceId);
        log.info("JWT token generated for user: {}", email);
        UserDto userDto = UserDto.builder()
                .id(dbUser.getId())
                .email(dbUser.getEmail())
                .fullName(dbUser.getFullName())
                .role(dbUser.getRole())
                .build();
        return new AuthResponse(token, userDto);
    }







    @Transactional(readOnly = true)
    public UserDto getMe(String email) {
        log.info("Fetching profile details for email: {}", email);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.error("Profile fetch failed: User {} not found", email);
                    return new RuntimeException("User not found");
                });
        return UserDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .build();
    }
}