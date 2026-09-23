package com.financetracker.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.financetracker.common.config.AppProperties;
import com.financetracker.common.security.JwtService;
import com.financetracker.user.User;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    @Test
    void roundTripsUserIdAndTokenVersion() {
        JwtService jwtService = jwtService();
        User user = new User();
        user.setId(42L);
        user.setEmail("ada@example.com");
        user.setTokenVersion(3);

        String token = jwtService.generate(user);
        JwtService.ParsedToken parsed = jwtService.parse(token);

        assertThat(parsed.userId()).isEqualTo(42L);
        assertThat(parsed.tokenVersion()).isEqualTo(3);
        assertThat(token).doesNotContain("password");
    }

    @Test
    void rejectsShortSecret() {
        AppProperties properties = new AppProperties(
                new AppProperties.Jwt("too-short", 60_000),
                new AppProperties.Cors("http://localhost:3000"),
                null,
                "Asia/Kolkata",
                "INR");
        assertThatThrownBy(() -> new JwtService(properties))
                .isInstanceOf(IllegalStateException.class);
    }

    private static JwtService jwtService() {
        return new JwtService(new AppProperties(
                new AppProperties.Jwt("test-jwt-secret-key-must-be-32-chars-min", 60_000),
                new AppProperties.Cors("http://localhost:3000"),
                null,
                "Asia/Kolkata",
                "INR"));
    }
}
