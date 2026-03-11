package com.owlynbackend.controller;

import com.owlynbackend.internal.dto.PublicSessionDTOs.GeneratePracticeReq;
import com.owlynbackend.internal.dto.PublicSessionDTOs.PublicSessionRes;
import com.owlynbackend.services.PublicSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public/sessions")
@RequiredArgsConstructor
public class PublicSessionController {

    private final PublicSessionService publicSessionService;

    @PostMapping("/practice")
    public ResponseEntity<PublicSessionRes> startPractice(@RequestBody GeneratePracticeReq req) {
        return ResponseEntity.ok(publicSessionService.createPracticeSession(req));
    }

    @PostMapping("/tutor")
    public ResponseEntity<PublicSessionRes> startTutor() {
        return ResponseEntity.ok(publicSessionService.createTutorSession());
    }
}