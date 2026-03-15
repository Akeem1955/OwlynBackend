package com.owlynbackend.controller;

import com.owlynbackend.internal.dto.WorkspaceDTOs.*;
import com.owlynbackend.services.WorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/workspace")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    @GetMapping
    public ResponseEntity<WorkspaceInfoRes> getWorkspace(@AuthenticationPrincipal UserDetails adminDetails) {
        return ResponseEntity.ok(workspaceService.getWorkspaceDetails(adminDetails));
    }

    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<WorkspaceInfoRes> updateWorkspace(
            @AuthenticationPrincipal UserDetails adminDetails,
            @RequestPart(value = "name", required = false) String name,
            @RequestPart(value = "logo", required = false) MultipartFile logo) {
        return ResponseEntity.ok(workspaceService.updateWorkspace(adminDetails, name, logo));
    }

    @PostMapping("/invite")
    public ResponseEntity<Map<String, String>> inviteRecruiter(
            @AuthenticationPrincipal UserDetails adminDetails,
            @RequestBody InviteReq req) {
        String message = workspaceService.inviteRecruiter(adminDetails, req);
        return ResponseEntity.ok(Map.of("message", message));
    }

    @GetMapping("/members")
    public ResponseEntity<List<MemberRes>> getMembers(@AuthenticationPrincipal UserDetails adminDetails) {
        return ResponseEntity.ok(workspaceService.getTeamMembers(adminDetails));
    }

    @DeleteMapping("/members/{userId}")
    public ResponseEntity<Map<String, String>> removeMember(
            @AuthenticationPrincipal UserDetails adminDetails,
            @PathVariable("userId") UUID userId) {
        workspaceService.removeMember(adminDetails, userId);
        return ResponseEntity.ok(Map.of("message", "Member successfully removed."));
    }
}