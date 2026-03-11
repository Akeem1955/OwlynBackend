package com.owlynbackend.internal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

public class WorkspaceDTOs {

    @Data
    public static class WorkspaceUpdateReq {
        private String name;
        private String logoUrl;
    }

    @Data
    public static class InviteReq {
        private String email;
        private String fullName;
    }

    @Data
    @Builder
    public static class MemberRes {
        private UUID userId;
        private String fullName;
        private String email;
        private String role;
    }

    @Data
    @Builder
    public static class WorkspaceInfoRes {
        private UUID workspaceId;
        private String name;
        private String logoUrl;
        private int memberCount;
    }
}