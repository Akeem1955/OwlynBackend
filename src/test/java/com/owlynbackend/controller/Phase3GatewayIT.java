package com.owlynbackend.controller;

import com.owlynbackend.internal.dto.CandidateDTOs.ValidateCodeReq;
import com.owlynbackend.internal.dto.CandidateDTOs.ValidateCodeRes;
import com.owlynbackend.services.InterviewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class Phase3GatewayIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    // We mock the service layer so we don't need a real Postgres connection for the CI pipeline
    @MockitoBean private InterviewService interviewService;

    @Test
    void testHealthCheck_Returns200() throws Exception {
        // Simple ping to ensure the Spring context booted and endpoints are public
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void testValidateCode_ReturnsToken() throws Exception {
        // Arrange
        ValidateCodeReq req = new ValidateCodeReq("839201");
        ValidateCodeRes res = ValidateCodeRes.builder()
                .token("guest.jwt.token")
                .interviewId(UUID.randomUUID())
                .title("Senior Java Developer")
                .durationMinutes(45)
                .toolsEnabled(Map.of("codeEditor", true))
                .build();

        when(interviewService.validateAccessCode("839201")).thenReturn(res);

        // Act & Assert
        mockMvc.perform(post("/api/interviews/validate-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("guest.jwt.token"))
                .andExpect(jsonPath("$.title").value("Senior Java Developer"));
    }

    @Test
    void testActivateInterview_Returns200() throws Exception {
        // Arrange
        doNothing().when(interviewService).startInterviewLockdown("839201");

        // Act & Assert (Testing the PUT endpoint)
        mockMvc.perform(put("/api/interviews/839201/status/active")
                        // Notice we don't send the JWT header here because we set it to permitAll in security config
                        // during the lockdown API (or handled via session).
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Interview is now ACTIVE. Lockdown initiated."));
    }
}