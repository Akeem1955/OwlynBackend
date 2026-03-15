package com.owlynbackend.controller;



import com.owlynbackend.internal.dto.PersonaDTOs.CreatePersonaReq;
import com.owlynbackend.internal.dto.PersonaDTOs.PersonaRes;
import com.owlynbackend.internal.dto.PersonaDTOs.UpdatePersonaReq;
import com.owlynbackend.internal.errors.InvalidRequestException;
import com.owlynbackend.services.AIPersonaService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails; // Add this import
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/personas")
@RequiredArgsConstructor
public class AIPersonaController {

    private final AIPersonaService aiPersonaService;
    private final ObjectMapper objectMapper;

    // Fetch all personas for the UI Sidebar
    @GetMapping
    public ResponseEntity<List<PersonaRes>> getPersonas(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(aiPersonaService.getWorkspacePersonas(userDetails));
    }

    // Create a new Persona (Handles both JSON data and File upload together)
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PersonaRes> createPersona(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestPart("persona") CreatePersonaReq req,
            @RequestPart(value = "file", required = false) MultipartFile file) {

        PersonaRes response = aiPersonaService.createPersona(userDetails, req, file);
        return ResponseEntity.ok(response);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PersonaRes> updatePersona(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable("id") UUID id,
            @RequestPart("persona") String personaJson,
            @RequestPart(value = "file", required = false) MultipartFile file) {

        try {
            UpdatePersonaReq req = objectMapper.readValue(personaJson, UpdatePersonaReq.class);
            return ResponseEntity.ok(aiPersonaService.updatePersona(userDetails, id, req, file));
        } catch (Exception e) {
            throw new InvalidRequestException("Invalid persona payload. Ensure 'persona' is valid JSON.");
        }
    }



    // Add inside AIPersonaController.java

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deletePersona(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable("id") UUID id) {

        aiPersonaService.deletePersona(userDetails, id);
        return ResponseEntity.ok(Map.of("message", "Persona successfully deleted."));
    }

}