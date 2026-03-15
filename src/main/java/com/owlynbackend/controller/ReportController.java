package com.owlynbackend.controller;

import com.owlynbackend.internal.dto.ReportDTOs.AddFeedbackReq;
import com.owlynbackend.internal.dto.ReportDTOs.ReportRes;
import com.owlynbackend.services.InterviewReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final InterviewReportService reportService;



    @GetMapping("/top")
    public ResponseEntity<ReportRes> getTopPerformer(
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(reportService.getTopPerformer(userDetails));
    }


    @GetMapping("/{interviewId}")
    public ResponseEntity<ReportRes> getReport(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable("interviewId") UUID interviewId) {

        return ResponseEntity.ok(reportService.getReport(userDetails, interviewId));
    }

    @PostMapping("/{interviewId}/feedback")
    public ResponseEntity<ReportRes> addFeedback(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable("interviewId") UUID interviewId,
            @RequestBody AddFeedbackReq req) {

        return ResponseEntity.ok(reportService.addHumanFeedback(userDetails, interviewId, req));
    }


    // Add inside ReportController.java

    @GetMapping
    public ResponseEntity<List<ReportRes>> getAllReports(
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(reportService.getAllWorkspaceReports(userDetails));
    }
}