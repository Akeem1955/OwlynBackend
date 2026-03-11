package com.owlynbackend;

import com.owlynbackend.config.security.JwtManager;
import com.owlynbackend.controller.AuthController;
import com.owlynbackend.internal.errors.InvalidOtpException;
import com.owlynbackend.internal.model.User;
import com.owlynbackend.services.AuthService;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
//import org.springframework.boot.test.autoconfigure.WebMvcTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false) // Bypass JWT filter for these public endpoints
public class AuthControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtManager jwtManager; // Needed to satisfy Spring Security Context

    @Test
    void testSignup_Returns200_AndCallsService() throws Exception {
        // Arrange
        User user = User.builder()
                .email("new@owlyn.com")
                .fullName("New User")
                .password("pass")
                .build();

        // Act & Assert 1: Verify HTTP 200 OK
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(user)))
                .andExpect(status().isOk());

        // Assert 2: Verify the controller actually delegated the work to the service layer
        // NOTE: We use initiateSignup() because that is the actual method in our AuthService
        verify(authService).initiateSignup(any(User.class));
    }

    @Test
    void testVerifySignup_WithBadOtp_Returns401() throws Exception {
        // Arrange: Force the service to throw our custom exception when called
        doThrow(new InvalidOtpException("Invalid or expired OTP"))
                .when(authService).verifySignupAndCreateWorkspace(eq("000000"), eq("hacker@owlyn.com"));

        // Act & Assert: Verify HTTP 401 Unauthorized and the correct JSON error message
        mockMvc.perform(post("/api/auth/verify-signup")
                        .param("otp", "000000")
                        .param("email", "hacker@owlyn.com"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid or expired OTP")); // Validates GlobalExceptionHandler!
    }
}