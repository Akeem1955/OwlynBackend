package com.owlynbackend.config.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

import java.io.IOException;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    private final UserDetailsService userDetailsService;
    private final JwtManager jwtManager;

    @Autowired
    public JwtAuthFilter(JwtManager jwtManager, UserDetailsService userDetailsService) {
        this.jwtManager = jwtManager;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        String token = null;

        // 1. Resolve Token from Header OR WebUtils Cookie
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        } else {
            Cookie cookie = WebUtils.getCookie(request, "accessToken");
            if (cookie != null && !cookie.getValue().isBlank()) {
                token = cookie.getValue();
            }
        }

        // 2. If token exists, attempt to validate and authenticate
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                // Extract role to determine authentication strategy
                String role = jwtManager.extractClaim(token, claims -> claims.get("role", String.class));
                UserDetails userDetails = null;

                // 3. The Ghost Candidate Bypass (No DB Lookup)
                if ("CANDIDATE".equals(role)) {
                    String accessCode = jwtManager.extractUserId(token);
                    userDetails = org.springframework.security.core.userdetails.User.builder()
                            .username(accessCode)
                            .password("") // Stateless ghost has no password
                            .authorities("ROLE_CANDIDATE")
                            .build();
                }
                // 4. Standard Staff Lookup (Hits Postgres)
                else {
                    String email = jwtManager.extractEmail(token);
                    if (email != null) {
                        userDetails = userDetailsService.loadUserByUsername(email);
                    }
                }

                // 5. Validate token expiry and inject into Security Context
                if (userDetails != null && !jwtManager.isTokenExpired(token)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }

            } catch (Exception e) {
                // 6. Active Error Handling: Log, Clear Cookie, and Block Request
                logger.error("JWT Validation Error: " + e.getMessage());

                // Actively clear the invalid cookie from the client's browser
                Cookie clearCookie = new Cookie("accessToken", null);
                clearCookie.setPath("/");
                clearCookie.setHttpOnly(true);
                clearCookie.setMaxAge(0);
                // clearCookie.setSecure(true); // Uncomment in production if using HTTPS
                response.addCookie(clearCookie);

                // Return a clean 401 JSON response and halt the filter chain
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\": \"Unauthorized: Invalid or expired token.\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}