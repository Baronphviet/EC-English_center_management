package com.englishcenter.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

class JwtServiceTest {
    private JwtService service() {
        return new JwtService("test-secret-that-is-at-least-32-bytes-long!!", 15);
    }

    @Test
    void generateAndParseRoundTrip() {
        JwtService jwt = service();
        String token = jwt.generate(7L, "parent.lan", "PARENT");
        Claims claims = jwt.parse(token);
        assertEquals("7", claims.getSubject());
        assertEquals("PARENT", claims.get("role", String.class));
        assertEquals("parent.lan", claims.get("username", String.class));
    }

    @Test
    void tamperedRoleFailsSignature() {
        JwtService jwt = service();
        String token = jwt.generate(4L, "student.an", "STUDENT");
        String[] parts = token.split("\\.");
        // Flip last char of payload segment to corrupt the signature binding.
        String payload = parts[1];
        char replacement = payload.charAt(payload.length() - 1) == 'A' ? 'B' : 'A';
        String tampered = parts[0] + "." + payload.substring(0, payload.length() - 1) + replacement + "." + parts[2];
        assertThrows(Exception.class, () -> jwt.parse(tampered));
    }

    @Test
    void shortSecretRejected() {
        assertThrows(IllegalArgumentException.class, () -> new JwtService("short", 15));
    }

    @Test
    void tokensAreNotPredictableSequence() {
        JwtService jwt = service();
        assertNotEquals("demo-token-1", jwt.generate(1L, "admin", "ADMIN"));
        assertTrue(jwt.generate(1L, "admin", "ADMIN").split("\\.").length == 3);
    }
}
