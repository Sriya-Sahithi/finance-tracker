package com.financetracker.common.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Runs after config files load so a Render {@code postgresql://} URL replaces
 * {@code spring.datasource.url} before the pool and Flyway start.
 */
public class RenderDatabaseEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String PROPERTY_SOURCE = "renderPostgresUrl";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String candidate = postgresUrl(
                environment.getProperty("DATABASE_URL"),
                environment.getProperty("spring.datasource.url"));
        if (candidate == null) {
            return;
        }
        PostgresJdbcUrl.Parsed parsed;
        try {
            parsed = PostgresJdbcUrl.parse(candidate).orElseThrow();
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(ex.getMessage());
        }
        Map<String, Object> overrides = new LinkedHashMap<>();
        overrides.put("spring.datasource.url", parsed.jdbcUrl());
        if (blank(environment.getProperty("DATABASE_USERNAME"))
                && blank(environment.getProperty("spring.datasource.username"))
                && !blank(parsed.username())) {
            overrides.put("spring.datasource.username", parsed.username());
        }
        if (blank(environment.getProperty("DATABASE_PASSWORD"))
                && blank(environment.getProperty("spring.datasource.password"))
                && parsed.password() != null) {
            overrides.put("spring.datasource.password", parsed.password());
        }
        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE, overrides));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    private static String postgresUrl(String databaseUrl, String datasourceUrl) {
        if (isPostgres(databaseUrl)) {
            return databaseUrl.trim();
        }
        if (isPostgres(datasourceUrl)) {
            return datasourceUrl.trim();
        }
        return null;
    }

    private static boolean isPostgres(String url) {
        if (url == null) {
            return false;
        }
        String trimmed = url.trim();
        return trimmed.startsWith("postgres://") || trimmed.startsWith("postgresql://");
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
