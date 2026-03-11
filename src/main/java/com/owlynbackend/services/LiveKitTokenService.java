package com.owlynbackend.services;

import io.livekit.server.AccessToken;
import io.livekit.server.CanPublish;
import io.livekit.server.CanPublishData;
import io.livekit.server.CanSubscribe;
import io.livekit.server.RoomJoin;
import io.livekit.server.RoomName;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LiveKitTokenService {

    @Value("${livekit.api.key}")
    private String apiKey;

    @Value("${livekit.api.secret}")
    private String apiSecret;


    public String generateCandidateToken(String interviewIdStr, String candidateId) {
        // Room name is simply the Interview UUID
        String roomName = "interview-" + interviewIdStr;

        AccessToken token = new AccessToken(apiKey, apiSecret);
        token.setIdentity(candidateId); // E.g., the 6-digit code "839201"
        token.setName("Candidate");

        // TRUE JAVA SDK SYNTAX: Pass individual grant objects via addGrants()
        token.addGrants(
                new RoomJoin(true),
                new RoomName(roomName),
                new CanPublish(true),
                new CanPublishData(true), // Needed so frontend can send "RUN_CODE"
                new CanSubscribe(true)    // Needed to hear the AI!
        );

        return token.toJwt();
    }


    public String generateRecruiterMonitorToken(String interviewIdStr, String recruiterEmail) {
        String roomName = "interview-" + interviewIdStr;

        AccessToken token = new AccessToken(apiKey, apiSecret);
        token.setIdentity(recruiterEmail);
        token.setName("Recruiter_Monitor");

        token.addGrants(
                new RoomJoin(true),
                new RoomName(roomName),
                new CanPublish(false),      // Cannot turn on camera/mic
                new CanPublishData(false),  // Cannot send data channel events
                new CanSubscribe(true)      // CAN watch the candidate!
        );

        return token.toJwt();
    }
}