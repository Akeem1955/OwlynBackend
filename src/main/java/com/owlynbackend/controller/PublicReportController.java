package com.owlynbackend.controller;

import com.owlynbackend.internal.errors.ReportNotReadyException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public/reports")
@RequiredArgsConstructor
public class PublicReportController {

    private final StringRedisTemplate stringRedisTemplate;

    @GetMapping("/{interviewId}")
    public ResponseEntity<String> getEphemeralReport(@PathVariable("interviewId") String interviewId) {
        String redisKey = "ephemeral_report:" + interviewId;
        String jsonReport = stringRedisTemplate.opsForValue().get(redisKey);

        if (jsonReport == null) {
            throw new ReportNotReadyException("Report not ready yet.");
        }

        // We return raw JSON string directly!
        return ResponseEntity.ok(jsonReport);
    }
}