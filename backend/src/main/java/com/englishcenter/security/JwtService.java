package com.englishcenter.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    // Published in earlier releases; never accept it again, even if explicitly configured.
    private static final String LEGACY_DEFAULT_SECRET = "change-me-to-32-bytes-minimum-secret!!";
    private final SecretKey key;
    private final long accessTtlMillis;

    public JwtService(
            @Value("${app.jwt.secret:}") String secret,
            @Value("${app.jwt.access-ttl-minutes:15}") long accessTtlMinutes) {
        if (secret == null || secret.isBlank() || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("Set JWT_SECRET (app.jwt.secret) to a random secret of at least 32 bytes");
        }
        if (LEGACY_DEFAULT_SECRET.equals(secret.strip())) {
            throw new IllegalArgumentException("JWT_SECRET (app.jwt.secret) must not use the published default secret");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTtlMillis = accessTtlMinutes * 60_000L;
    }

    public String generate(Long userId, String username, String role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTtlMillis))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) throws JwtException {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
