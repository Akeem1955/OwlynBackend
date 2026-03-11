package com.owlynbackend.services;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import lombok.extern.slf4j.Slf4j;

@ExtendWith(MockitoExtension.class)
@Slf4j
public class OtpServiceTest {

    @Mock
    private JavaMailSender javaMailSender;

    @InjectMocks
    private OtpService otpService;

    @Test
    void sendEmailOtp_Success_GeneratesRandomOtp() {
        log.info("Starting test: sendEmailOtp_Success_GeneratesRandomOtp");
        // Arrange
        String email = "user@example.com";
        log.debug("Arranged standard email: {}", email);
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);

        log.info("Acting: otpService.sendEmailOtp()");
        // Act
        String otp = otpService.sendEmailOtp(email);

        log.info("Asserting: Verifying random 6-digit OTP generation and mail sending");
        // Assert
        assertNotNull(otp);
        assertEquals(6, otp.length());
        verify(javaMailSender, times(1)).createMimeMessage();
        verify(javaMailSender, times(1)).send(any(MimeMessage.class));
        log.info("Test passed: sendEmailOtp_Success_GeneratesRandomOtp - OTP: {}", otp);
    }

    @Test
    void sendEmailOtp_AdminBackdoor_ReturnsFixedOtp() {
        log.info("Starting test: sendEmailOtp_AdminBackdoor_ReturnsFixedOtp");
        // Arrange
        String adminEmail = "owlyn.admin@gmail.com";
        log.debug("Arranged admin backdoor email: {}", adminEmail);

        log.info("Acting: otpService.sendEmailOtp()");
        // Act
        String otp = otpService.sendEmailOtp(adminEmail);

        log.info("Asserting: Verifying fixed backdoor OTP '123456'");
        // Assert
        assertEquals("123456", otp);
        verify(javaMailSender, never()).createMimeMessage();
        verify(javaMailSender, never()).send(any(MimeMessage.class));
        log.info("Test passed: sendEmailOtp_AdminBackdoor_ReturnsFixedOtp");
    }

    @Test
    void sendEmailOtp_Fails_IfMailSenderExplodes() {
        log.info("Starting test: sendEmailOtp_Fails_IfMailSenderExplodes");
        // Arrange
        String email = "error@example.com";
        log.debug("Arranged email causing mail server error: {}", email);
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new RuntimeException("Mail server down")).when(javaMailSender).send(any(MimeMessage.class));

        log.info("Acting & Asserting: Expecting RuntimeException due to mail server failure");
        // Act & Assert
        assertThrows(RuntimeException.class, () -> otpService.sendEmailOtp(email));
        log.info("Test passed: sendEmailOtp_Fails_IfMailSenderExplodes");
    }
}
