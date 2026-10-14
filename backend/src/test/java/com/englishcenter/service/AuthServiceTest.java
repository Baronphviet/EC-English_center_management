package com.englishcenter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.englishcenter.entity.UserAccount;
import com.englishcenter.repository.UserRepository;
import com.englishcenter.security.JwtService;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock
    UserRepository users;
    @Mock
    JwtService jwtService;

    PasswordEncoder encoder = new BCryptPasswordEncoder();
    AuthService service;

    @BeforeEach
    void setup() {
        service = new AuthService(users, encoder, jwtService);
    }

    private UserAccount activeUser(String storedHash) {
        UserAccount user = new UserAccount();
        user.id = 1L;
        user.username = "admin";
        user.passwordHash = storedHash;
        user.fullName = "Center Admin";
        user.role = "ADMIN";
        user.active = true;
        return user;
    }

    @Test
    void bcryptLoginIssuesSignedToken() {
        UserAccount user = activeUser(encoder.encode("admin"));
        when(users.findByUsername("admin")).thenReturn(Optional.of(user));
        when(jwtService.generate(1L, "admin", "ADMIN")).thenReturn("header.payload.sig");

        Map<String, Object> result = service.login(new AuthService.LoginRequest("admin", "admin"));

        assertEquals("header.payload.sig", result.get("token"));
        assertEquals("ADMIN", result.get("role"));
    }

    @Test
    void legacyPlaintextLoginUpgradesToBcrypt() {
        UserAccount user = activeUser("admin");
        when(users.findByUsername("admin")).thenReturn(Optional.of(user));
        when(jwtService.generate(1L, "admin", "ADMIN")).thenReturn("header.payload.sig");

        Map<String, Object> result = service.login(new AuthService.LoginRequest("admin", "admin"));

        assertEquals("header.payload.sig", result.get("token"));
        ArgumentCaptor<UserAccount> saved = ArgumentCaptor.forClass(UserAccount.class);
        verify(users).save(saved.capture());
        assertTrue(saved.getValue().passwordHash.startsWith("$2a$"));
    }

    @Test
    void wrongPasswordRejectedWithGenericMessage() {
        UserAccount user = activeUser(encoder.encode("admin"));
        when(users.findByUsername("admin")).thenReturn(Optional.of(user));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.login(new AuthService.LoginRequest("admin", "wrong")));
        assertEquals("Invalid username or password", ex.getMessage());
    }

    @Test
    void nullRequestRejectedWithoutNpe() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.login(new AuthService.LoginRequest(null, null)));
        assertEquals("Invalid username or password", ex.getMessage());
    }

    @Test
    void lockedAccountRejected() {
        UserAccount user = activeUser(encoder.encode("admin"));
        user.active = false;
        when(users.findByUsername("admin")).thenReturn(Optional.of(user));

        assertThrows(IllegalArgumentException.class,
                () -> service.login(new AuthService.LoginRequest("admin", "admin")));
    }

    @Test
    void responseNeverContainsPassword() {
        UserAccount user = activeUser(encoder.encode("admin"));
        when(users.findByUsername("admin")).thenReturn(Optional.of(user));
        when(jwtService.generate(any(), any(), any())).thenReturn("t");

        Map<String, Object> result = service.login(new AuthService.LoginRequest("admin", "admin"));

        assertTrue(result.keySet().stream().noneMatch(k -> k.equalsIgnoreCase("password")));
    }
}
