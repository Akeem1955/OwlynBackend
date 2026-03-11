package com.owlynbackend.controller;

import com.owlynbackend.internal.errors.InvalidRequestException;
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
    public ResponseEntity<String> getEphemeralReport(@PathVariable String interviewId) {
        String redisKey = "ephemeral_report:" + interviewId;
        String jsonReport = stringRedisTemplate.opsForValue().get(redisKey);

        if (jsonReport == null) {
            // It either expired, or Agent 4 is still thinking (usually takes ~5 seconds)
            throw new InvalidRequestException("Report not found or Agent 4 is still generating it. Please wait and retry.");
        }

        // We return raw JSON string directly!
        return ResponseEntity.ok(jsonReport);
    }
}