package com.owlynbackend.config.security;




import com.owlynbackend.internal.model.User;
import com.owlynbackend.internal.model.enums.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Component
public class JwtManager {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.issuer:owlyn}")
    private String jwtIssuer;

    public JwtManager() {}

    // Updated to match Phase 1 Spec: {sub: userId, email, role, workspaceId, iat, exp}
    public String generateToken(User user, UUID workspaceId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", user.getEmail());
        claims.put("role", user.getRole().name());
        if (workspaceId != null) {
            claims.put("workspaceId", workspaceId.toString());
        }

        return createToken(claims, user.getId().toString()); // using userId as "sub"
    }

    private String createToken(Map<String, Object> claims, String subject) {
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuer(jwtIssuer)
                .issuedAt(new Date(System.currentTimeMillis()))
                // Spec says exactly 24 hours expiry
                .expiration(new Date(System.currentTimeMillis() + 1000L * 60 * 60 * 24))
                .signWith(getSecretKey())
                .compact();
    }
    private SecretKey getSecretKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    // "sub" holds the UserId per the spec
    public String extractUserId(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    // We extract email directly from the custom claims
    public String extractEmail(String token) {
        final Claims claims = extractAllClaims(token);
        return claims.get("email", String.class);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSecretKey())
                .build()
                .parseSignedClaims(token).getPayload();
    }

    public Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public Boolean validateToken(String token, UserDetails userDetails) {
        // We use extractEmail here because UserDetails.getUsername() usually returns the email in Spring
        final String email = extractEmail(token);
        return (email.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }

    // Phase 3: Generates a temporary 2-hour Guest JWT for the Candidate
    public String generateCandidateToken(String accessCode, String interviewId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", Role.CANDIDATE.name());
        claims.put("interviewId", interviewId); // So we know which room they are in

        return Jwts.builder()
                .claims(claims)
                .subject(accessCode) // We use the 6-digit code as the "username/subject"
                .issuer(jwtIssuer)
                .issuedAt(new Date(System.currentTimeMillis()))
                // Expires in exactly 2 hours (enough for a 90 min interview)
                .expiration(new Date(System.currentTimeMillis() + 1000L * 60 * 60 * 2))
                .signWith(getSecretKey())
                .compact();
    }
}