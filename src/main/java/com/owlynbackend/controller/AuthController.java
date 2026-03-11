package com.owlynbackend.controller;



import com.owlynbackend.internal.dto.AuthResponse;
import com.owlynbackend.internal.dto.UserDto;
import com.owlynbackend.internal.model.User;
import com.owlynbackend.services.AuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@Slf4j
public class AuthController {

    private final AuthService authService;

    @Autowired
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // Step 1: Send OTP for Signup
    @PostMapping("/signup")
    public ResponseEntity<String> signup(@RequestBody User user) {
        log.info("POST /api/auth/signup - Received request for email: {}", user.getEmail());
        authService.initiateSignup(user);
        log.info("POST /api/auth/signup - Response: 200 OK - OTP Sent Successfully to {}", user.getEmail());
        return ResponseEntity.ok("OTP Sent Successfully");
    }

    // Step 2: Verify OTP and create User + Workspace
    @PostMapping("/verify-signup")
    public ResponseEntity<AuthResponse> verifySignup(@RequestParam String otp, @RequestParam String email) {
        log.info("POST /api/auth/verify-signup - Received request for email: {}", email);
        AuthResponse response = authService.verifySignupAndCreateWorkspace(otp, email);
        log.info("POST /api/auth/verify-signup - Response: 200 OK - Signup successful for {}", email);
        return ResponseEntity.ok(response);
    }

    // Step 1: Send OTP for Login
    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        log.info("POST /api/auth/login - Received request for email: {}", email);
        authService.initiateLogin(email, request.get("password"));
        log.info("POST /api/auth/login - Response: 200 OK - Login OTP sent successfully to {}", email);
        return ResponseEntity.ok("OTP Sent Successfully");
    }

    // Step 2: Verify Login OTP
    @PostMapping("/verify-login")
    public ResponseEntity<AuthResponse> verifyLogin(@RequestParam String otp, @RequestParam String email) {
        log.info("POST /api/auth/verify-login - Received request for email: {}", email);
        AuthResponse response = authService.verifyLogin(otp, email);
        log.info("POST /api/auth/verify-login - Response: 200 OK - Login verification successful for {}", email);
        return ResponseEntity.ok(response);
    }

    // Hackathon Checkpoint B1.3: GET /api/auth/me
    @GetMapping("/me")
    public ResponseEntity<UserDto> me(@AuthenticationPrincipal UserDetails principal) {
        log.info("GET /api/auth/me - Received request for user: {}", principal.getUsername());
        UserDto userDto = authService.getMe(principal.getUsername());
        log.info("GET /api/auth/me - Response: 200 OK - Profile fetched for {}", principal.getUsername());
        return ResponseEntity.ok(userDto);
    }
}