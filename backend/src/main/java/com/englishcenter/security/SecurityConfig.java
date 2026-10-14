package com.englishcenter.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    private final JwtAuthFilter jwtAuthFilter;

    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:5173}")
    private String allowedOrigins;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) -> {
                            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            res.setContentType("application/json");
                            res.getWriter().write(
                                    "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}");
                        })
                        .accessDeniedHandler((req, res, e) -> {
                            res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            res.setContentType("application/json");
                            res.getWriter().write(
                                    "{\"status\":403,\"error\":\"Forbidden\",\"message\":\"Access denied\"}");
                        }))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login", "/api/health").permitAll()
                        .requestMatchers("/api/public/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/announcements/slider").permitAll()
                        // Admin-only identity management (lists are ADMIN; single profile is owner-or-ADMIN in service)
                        .requestMatchers(HttpMethod.GET, "/api/admins", "/api/admins/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/admins", "/api/admins/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/admins/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/users/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/users/**/activate").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/teachers", "/api/students", "/api/parents",
                                "/api/parents/link-student", "/api/parents/unlink-student")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/teachers/**", "/api/students/**", "/api/parents/**")
                        .hasRole("ADMIN")
                        // Writes on finance/master/announcements/email/salary are ADMIN-only
                        .requestMatchers(HttpMethod.POST, "/api/monthly-fees", "/api/monthly-fees/**",
                                "/api/payments", "/api/payments/**")
                        .hasRole("ADMIN")
                        .requestMatchers("/api/admin/email/**", "/api/bank-payment-config/**",
                                "/api/teacher-salary/**", "/api/master/**")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/announcements", "/api/announcements/**")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/announcements", "/api/announcements/**")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/announcements", "/api/announcements/**")
                        .hasRole("ADMIN")
                        // Reads: authenticated, with service-level scoping (fees, profile) where applicable
                        .requestMatchers("/api/classes/**", "/api/enrollments/**").authenticated()
                        .requestMatchers("/api/attendance/**", "/api/teacher/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/monthly-fees", "/api/monthly-fees/**",
                                "/api/payments", "/api/payments/**", "/api/reports/**", "/api/dashboard/**",
                                "/api/announcements", "/api/announcements/**")
                        .authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/teachers", "/api/students", "/api/parents")
                        .authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/users/*/profile").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/users/*/password").authenticated()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        for (String origin : allowedOrigins.split(",")) {
            String trimmed = origin.trim();
            if (!trimmed.isEmpty()) {
                config.addAllowedOrigin(trimmed);
            }
        }
        config.addAllowedMethod("GET");
        config.addAllowedMethod("POST");
        config.addAllowedMethod("PUT");
        config.addAllowedMethod("PATCH");
        config.addAllowedMethod("DELETE");
        config.addAllowedMethod("OPTIONS");
        config.addAllowedHeader("*");
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
