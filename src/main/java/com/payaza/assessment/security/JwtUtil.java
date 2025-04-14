package com.payaza.assessment.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * Utility class for JWT generation and validation.
 */
@Component
public class JwtUtil {
    private static final Logger logger = LoggerFactory.getLogger(JwtUtil.class);
    private static final String JWT_SECRET;
    private static final long EXPIRATION_TIME = 86400000; // 1 day

    static {
        JWT_SECRET = getJwtSecret();
    }

    /**
     * Mock Secrets Manager for local testing.
     * @return The JWT secret key.
     */
    private static String getJwtSecret() {
        logger.info("Using mock Secrets Manager for JWT secret");
        return "my-test-jwt-secret-key-1234567890abcdef"; // Test key for development Secrets manager is ideal for production
    }

    /**
     * Generates a JWT token for a user.
     * @param username The username to include in the token.
     * @return The JWT token.
     */
    public String generateToken(String username) {
        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(SignatureAlgorithm.HS256, JWT_SECRET)
                .compact();
    }

    /**
     * Validates a JWT token and extracts the username.
     * @param token The JWT token.
     * @return The username.
     * @throws RuntimeException if the token is invalid.
     */
    public String validateToken(String token) {
        try {
            String username = Jwts.parser()
                    .setSigningKey(JWT_SECRET)
                    .parseClaimsJws(token)
                    .getBody()
                    .getSubject();
            if (username == null) {
                throw new IllegalArgumentException("JWT missing subject");
            }
            return username;
        } catch (Exception e) {
            logger.error("Invalid JWT token", e);
            throw new RuntimeException("Invalid JWT token", e);
        }
    }
}