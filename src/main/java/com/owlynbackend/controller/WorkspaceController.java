package com.owlynbackend.controller;

import com.owlynbackend.internal.dto.WorkspaceDTOs.*;
import com.owlynbackend.services.WorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

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

    @PutMapping
    public ResponseEntity<WorkspaceInfoRes> updateWorkspace(
            @AuthenticationPrincipal UserDetails adminDetails,
            @RequestBody WorkspaceUpdateReq req) {
        return ResponseEntity.ok(workspaceService.updateWorkspace(adminDetails, req));
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
            @PathVariable UUID userId) {
        workspaceService.removeMember(adminDetails, userId);
        return ResponseEntity.ok(Map.of("message", "Member successfully removed."));
    }
}