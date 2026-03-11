package com.owlynbackend.controller;

import com.owlynbackend.internal.dto.CopilotDTOs.CopilotReq;
import com.owlynbackend.internal.dto.CopilotDTOs.CopilotRes;
import com.owlynbackend.services.CopilotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/copilot")
@RequiredArgsConstructor
public class CopilotController {

    private final CopilotService copilotService;

    @PostMapping
    public ResponseEntity<CopilotRes> getSuggestion(
            @RequestBody CopilotReq req) {

        return ResponseEntity.ok(copilotService.generateSuggestion(req));
    }
}