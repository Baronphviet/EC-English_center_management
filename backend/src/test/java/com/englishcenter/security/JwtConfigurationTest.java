package com.englishcenter.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

class JwtConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(context -> {
                // Do not let credentials on the developer/CI machine mask missing configuration.
                var sources = context.getEnvironment().getPropertySources();
                sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
                sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
            })
            .withUserConfiguration(JwtConfiguration.class);

    private ApplicationContextRunner withApplicationYaml() {
        return runner.withInitializer(context -> {
            try {
                var sources = new YamlPropertySourceLoader()
                        .load("application", new ClassPathResource("application.yml"));
                sources.forEach(context.getEnvironment().getPropertySources()::addLast);
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        });
    }

    @Test
    void missingJwtSecretPreventsStartupWithApplicationYaml() {
        withApplicationYaml().run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class);
        });
    }

    @Test
    void missingJwtSecretPreventsStartupWithoutApplicationYaml() {
        runner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "short", "change-me-to-32-bytes-minimum-secret!!"})
    void invalidEnvironmentSecretPreventsStartup(String secret) {
        withApplicationYaml().withPropertyValues("JWT_SECRET=" + secret)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class);
                });
    }

    @Test
    void environmentSecretIsUsedForSigning() {
        String secret = "configuration-test-only-secret-at-least-32-bytes";
        withApplicationYaml().withPropertyValues("JWT_SECRET=" + secret).run(context -> {
            assertThat(context).hasNotFailed();
            String token = context.getBean(JwtService.class).generate(7L, "admin", "ADMIN");
            var claims = Jwts.parser()
                    .verifyWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                    .build().parseSignedClaims(token).getPayload();
            assertThat(claims.getSubject()).isEqualTo("7");
            assertThat(claims.get("role", String.class)).isEqualTo("ADMIN");
        });
    }

    @Configuration(proxyBeanMethods = false)
    @Import(JwtService.class)
    static class JwtConfiguration {
        @Bean
        static PropertySourcesPlaceholderConfigurer placeholders() {
            return new PropertySourcesPlaceholderConfigurer();
        }
    }
}
