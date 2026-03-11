package com.owlynbackend.controller;

import com.owlynbackend.config.security.JwtManager;
import com.owlynbackend.internal.dto.WorkspaceDTOs.InviteReq;
import com.owlynbackend.services.WorkspaceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WorkspaceController.class)
@AutoConfigureMockMvc(addFilters = false)
public class WorkspaceControllerIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private WorkspaceService workspaceService;
    @MockitoBean private JwtManager jwtManager;

    @Test
    void inviteRecruiter_Returns200_AndCallsService() throws Exception {
        // Arrange
        InviteReq req = new InviteReq();
        req.setEmail("amina@techcorp.com");
        req.setFullName("Amina");

        when(workspaceService.inviteRecruiter(any(), any(InviteReq.class)))
                .thenReturn("Invite successful! Temporary password: testpass");

        // Act & Assert HTTP
        mockMvc.perform(post("/api/workspace/invite")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Invite successful! Temporary password: testpass"));

        // Assert Controller passed data to Service safely (The Senior's Rule)
        verify(workspaceService).inviteRecruiter(any(), any(InviteReq.class));
    }
}