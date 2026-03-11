package com.owlynbackend.controller;

import com.owlynbackend.config.security.JwtManager;
import com.owlynbackend.internal.dto.InterviewConfigDTOs.CreateInterviewReq;
import com.owlynbackend.internal.dto.InterviewConfigDTOs.InterviewCreatedRes;
import com.owlynbackend.internal.dto.InterviewDTOs.GenerateQuestionsReq;
import com.owlynbackend.internal.dto.InterviewDTOs.GenerateQuestionsRes;
import com.owlynbackend.services.GeminiAgentService;
import com.owlynbackend.services.InterviewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InterviewController.class)
@AutoConfigureMockMvc(addFilters = false)
public class InterviewControllerIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private InterviewService interviewService;
    @MockitoBean private GeminiAgentService geminiAgentService;
    @MockitoBean private JwtManager jwtManager;

    @Test
    void generateQuestions_Returns200_AndCallsGeminiService() throws Exception {
        // Arrange
        GenerateQuestionsReq req = new GenerateQuestionsReq("Senior Java", "Focus on AOP", 5);
        GenerateQuestionsRes mockedRes = new GenerateQuestionsRes("1. What is Spring AOP?");

        when(geminiAgentService.draftQuestions(any(GenerateQuestionsReq.class))).thenReturn(mockedRes);

        // Act & Assert HTTP
        mockMvc.perform(post("/api/interviews/generate-questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.draftedQuestions").value("1. What is Spring AOP?"));

        // Assert Controller delegated to the Gemini Service
        verify(geminiAgentService).draftQuestions(any(GenerateQuestionsReq.class));
    }

    @Test
    void createInterview_Returns200_AndReturnsAccessCode() throws Exception {
        // Arrange
        CreateInterviewReq req = new CreateInterviewReq();
        req.setTitle("Senior Java");
        req.setGeneratedQuestions("1. What is AOP?");

        InterviewCreatedRes mockedRes = InterviewCreatedRes.builder()
                .interviewId(UUID.randomUUID())
                .title("Senior Java")
                .accessCode("839201")
                .status("UPCOMING")
                .build();

        when(interviewService.createInterview(any(), any(CreateInterviewReq.class))).thenReturn(mockedRes);

        // Act & Assert HTTP
        mockMvc.perform(post("/api/interviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessCode").value("839201"));

        // Assert Controller successfully delegated creation to InterviewService
        verify(interviewService).createInterview(any(), any(CreateInterviewReq.class));
    }
}