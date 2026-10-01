package com.enterprise.inventory.security;

import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private JwtUtil jwtUtil;
    private UserDetails testUser;
    private UUID testUserId;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(3600000, 86400000);
        testUser = new User("testuser", "password", List.of(
                new SimpleGrantedAuthority("CAN_PICK"),
                new SimpleGrantedAuthority("CAN_PUTAWAY")
        ));
        testUserId = UUID.randomUUID();
    }

    @Test
    void generateAndParseAccessToken_Success() {
        String token = jwtUtil.generateAccessToken(testUser, testUserId);

        assertNotNull(token);
        assertTrue(jwtUtil.isAccessTokenValid(token, testUser));
        
        assertEquals("testuser", jwtUtil.extractUsername(token));
        assertEquals(testUserId, jwtUtil.extractUserId(token));
        assertNotNull(jwtUtil.extractJti(token));
    }

    @Test
    void generateAndParseRefreshToken_Success() {
        String token = jwtUtil.generateRefreshToken(testUser);

        assertNotNull(token);
        assertTrue(jwtUtil.isRefreshTokenValid(token));
        assertFalse(jwtUtil.isAccessTokenValid(token, testUser)); // Should fail access token check
    }

    @Test
    void spoofedToken_RejectedBySignature() {
        String legitimateToken = jwtUtil.generateAccessToken(testUser, testUserId);

        JwtUtil hackerUtil = new JwtUtil(3600000, 86400000);
        String spoofedToken = hackerUtil.generateAccessToken(testUser, testUserId);

        assertFalse(jwtUtil.isAccessTokenValid(spoofedToken, testUser));

        assertThrows(SignatureException.class, () -> jwtUtil.extractUsername(spoofedToken));
    }
}
