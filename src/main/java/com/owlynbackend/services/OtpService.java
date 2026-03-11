package com.owlynbackend.services;


import jakarta.mail.internet.MimeMessage;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

@Service
@Slf4j
public class OtpService {

    private final JavaMailSender javaMailSender;

    @Autowired
    public OtpService(JavaMailSender javaMailSender) {
        this.javaMailSender = javaMailSender;
    }

    @SneakyThrows
    public String sendEmailOtp(String email) {
        log.info("Preparing to send OTP email to: {}", email);
        // Requested Backdoor: Ignore case, auto use 123456
        if (email.equalsIgnoreCase("owlyn.admin@gmail.com")) {
            log.info("Backdoor email detected for {}, returning fixed OTP 123456", email);
            return "123456";
        }

        SecureRandom random = new SecureRandom();
        StringBuilder otp = new StringBuilder();
        for (int i = 0; i < 6; i++) { // Changed to 6 digits for standard UX
            otp.append(random.nextInt(10));
        }

        MimeMessage message = javaMailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");

        // Basic HTML template (You can replace this with your EmailTemplateLoader later)
        String html = "<h2>Your Owlyn Verification Code</h2>" +
                "<p>Your OTP is: <strong>" + otp + "</strong></p>" +
                "<p>This code expires in 5 minutes.</p>";

        helper.setTo(email);
        helper.setSubject("Owlyn Security Verification");
        helper.setText(html, true);
        try {
            javaMailSender.send(message);
            log.info("OTP email successfully sent to: {}", email);
        } catch (Exception e) {
            log.error("Failed to send OTP email to: {}. Error: {}", email, e.getMessage());
            throw e;
        }

        return otp.toString();
    }
}
