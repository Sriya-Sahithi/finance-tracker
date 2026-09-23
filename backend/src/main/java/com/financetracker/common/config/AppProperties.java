package com.financetracker.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Cors cors, Encryption encryption, String timezone, String currency) {

    public record Jwt(String secret, long expirationMs) {
    }

    public record Cors(String allowedOrigins) {
    }

    public record Encryption(String key) {
    }
}
