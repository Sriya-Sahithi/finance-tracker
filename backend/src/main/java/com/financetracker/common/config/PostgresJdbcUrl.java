package com.financetracker.common.config;

import java.net.URI;
import java.util.Optional;

/**
 * Turns a Render Postgres URL into the JDBC URL Spring Boot expects.
 * Render's connection string is {@code postgresql://user:password@host:port/database}.
 */
public final class PostgresJdbcUrl {

    private PostgresJdbcUrl() {
    }

    public record Parsed(String jdbcUrl, String username, String password) {
    }

    public static Optional<Parsed> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("jdbc:")) {
            return Optional.empty();
        }
        if (!trimmed.startsWith("postgres://") && !trimmed.startsWith("postgresql://")) {
            throw new IllegalArgumentException(
                    "DATABASE_URL must start with jdbc:postgresql://, postgres://, or postgresql://");
        }
        URI uri = parseUri(trimmed);
        if (uri.getHost() == null || uri.getPath() == null || uri.getPath().length() < 2) {
            throw new IllegalArgumentException("DATABASE_URL is missing a host or database name");
        }
        StringBuilder jdbc = new StringBuilder("jdbc:postgresql://")
                .append(uri.getHost());
        if (uri.getPort() > 0) {
            jdbc.append(':').append(uri.getPort());
        }
        jdbc.append(uri.getPath());
        if (uri.getRawQuery() != null && !uri.getRawQuery().isEmpty()) {
            jdbc.append('?').append(uri.getRawQuery());
        }
        String username = null;
        String password = null;
        String userInfo = uri.getUserInfo();
        if (userInfo != null && !userInfo.isEmpty()) {
            int colon = userInfo.indexOf(':');
            if (colon < 0) {
                username = userInfo;
            } else {
                username = userInfo.substring(0, colon);
                password = userInfo.substring(colon + 1);
            }
        }
        return Optional.of(new Parsed(jdbc.toString(), username, password));
    }

    private static URI parseUri(String trimmed) {
        try {
            return URI.create(trimmed);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("DATABASE_URL is not a valid postgres URL");
        }
    }
}
