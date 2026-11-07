package com.englishcenter.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(properties = "app.jwt.secret=http-test-only-secret-at-least-32-bytes-long")
@ContextConfiguration(classes = {
        JwtSecurityIntegrationTest.ProtectedController.class,
        SecurityConfig.class, JwtAuthFilter.class, JwtService.class
})
class JwtSecurityIntegrationTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    JwtService jwt;

    @Test
    void adminTokenSignedWithPublishedDefaultIsUnauthorized() throws Exception {
        // Construct outside JwtService: an attacker does not use our configuration validation.
        String forged = Jwts.builder()
                .subject("1").claim("role", "ADMIN")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(
                        "change-me-to-32-bytes-minimum-secret!!".getBytes(StandardCharsets.UTF_8)))
                .compact();
        mvc.perform(get("/api/admins").header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminTokenSignedWithConfiguredSecretIsAccepted() throws Exception {
        mvc.perform(get("/api/admins")
                        .header("Authorization", "Bearer " + jwt.generate(1L, "admin", "ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void validStudentTokenCannotAccessAdminEndpoint() throws Exception {
        mvc.perform(get("/api/admins")
                        .header("Authorization", "Bearer " + jwt.generate(2L, "student", "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @RestController
    static class ProtectedController {
        @GetMapping("/api/admins")
        String admins() {
            return "admin-only";
        }
    }
}
