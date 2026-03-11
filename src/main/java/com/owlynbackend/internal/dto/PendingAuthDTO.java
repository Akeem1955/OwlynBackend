package com.owlynbackend.internal.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingAuthDTO implements Serializable {
    private String email;
    private String fullName;
    private String rawPassword;
    private String expectedOtp;
}