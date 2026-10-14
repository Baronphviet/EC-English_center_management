package com.englishcenter.service;

import com.englishcenter.entity.UserAccount;
import com.englishcenter.repository.UserRepository;
import com.englishcenter.security.JwtService;
import jakarta.validation.constraints.NotBlank;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public Map<String, Object> login(LoginRequest request) {
        if (request == null || isBlank(request.username()) || isBlank(request.password())) {
            throw new IllegalArgumentException("Invalid username or password");
        }
        String username = request.username().trim();
        UserAccount user = users.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Invalid username or password"));
        if (!matchesWithLegacyUpgrade(user, request.password())) {
            throw new IllegalArgumentException("Invalid username or password");
        }
        if (!Boolean.TRUE.equals(user.active)) {
            throw new IllegalArgumentException("Tài khoản bị khóa, vui lòng liên hệ admin để được đăng nhập");
        }
        String token = jwtService.generate(user.id, user.username, user.role);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", user.id);
        map.put("username", user.username);
        map.put("fullName", user.fullName);
        map.put("role", user.role);
        map.put("token", token);
        return map;
    }

    private boolean matchesWithLegacyUpgrade(UserAccount user, String rawPassword) {
        String stored = user.passwordHash == null ? "" : user.passwordHash;
        // BCrypt hashes start with $2a$/$2b$/$2y$.
        if (stored.startsWith("$2a$") || stored.startsWith("$2b$") || stored.startsWith("$2y$")) {
            return passwordEncoder.matches(rawPassword, stored);
        }
        // Legacy plaintext fallback: verify then transparently upgrade to BCrypt.
        if (!stored.equals(rawPassword)) {
            return false;
        }
        user.passwordHash = passwordEncoder.encode(rawPassword);
        users.save(user);
        return true;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
