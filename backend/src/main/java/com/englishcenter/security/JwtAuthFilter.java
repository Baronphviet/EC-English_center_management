package com.englishcenter.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            // Legacy demo tokens are rejected: they carry no signature.
            if (!token.startsWith("demo-token-")) {
                try {
                    Claims claims = jwtService.parse(token);
                    String role = claims.get("role", String.class);
                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                            claims.getSubject(),
                            null,
                            role == null ? List.of() : List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                    auth.setDetails(claims);
                    SecurityContextHolder.getContext().setAuthentication(auth);
                } catch (JwtException | IllegalArgumentException ex) {
                    SecurityContextHolder.clearContext();
                }
            } else {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
