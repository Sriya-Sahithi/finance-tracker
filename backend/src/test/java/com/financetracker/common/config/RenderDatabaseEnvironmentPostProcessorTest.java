package com.financetracker.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

class RenderDatabaseEnvironmentPostProcessorTest {

    @Test
    void rewritesRenderUrlAndFillsCredentialsWhenSeparateVarsAreAbsent() {
        ConfigurableEnvironment environment = environment(Map.of(
                "DATABASE_URL", "postgresql://finance:s3cret@dpg-abc-a:5432/finance_tracker",
                "spring.datasource.url", "postgresql://finance:s3cret@dpg-abc-a:5432/finance_tracker",
                "spring.datasource.username", "",
                "spring.datasource.password", ""));

        new RenderDatabaseEnvironmentPostProcessor().postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://dpg-abc-a:5432/finance_tracker");
        assertThat(environment.getProperty("spring.datasource.username")).isEqualTo("finance");
        assertThat(environment.getProperty("spring.datasource.password")).isEqualTo("s3cret");
    }

    @Test
    void keepsExplicitUsernameAndPassword() {
        ConfigurableEnvironment environment = environment(Map.of(
                "DATABASE_URL", "postgres://embedded:embedded-pass@dpg-abc-a:5432/finance_tracker",
                "DATABASE_USERNAME", "finance",
                "DATABASE_PASSWORD", "from-render",
                "spring.datasource.url", "postgres://embedded:embedded-pass@dpg-abc-a:5432/finance_tracker",
                "spring.datasource.username", "finance",
                "spring.datasource.password", "from-render"));

        new RenderDatabaseEnvironmentPostProcessor().postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://dpg-abc-a:5432/finance_tracker");
        assertThat(environment.getProperty("spring.datasource.username")).isEqualTo("finance");
        assertThat(environment.getProperty("spring.datasource.password")).isEqualTo("from-render");
    }

    @Test
    void ignoresJdbcUrls() {
        ConfigurableEnvironment environment = environment(Map.of(
                "DATABASE_URL", "jdbc:postgresql://localhost:5432/finance_tracker",
                "spring.datasource.url", "jdbc:postgresql://localhost:5432/finance_tracker",
                "spring.datasource.username", "finance",
                "spring.datasource.password", "finance"));

        new RenderDatabaseEnvironmentPostProcessor().postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getPropertySources().contains(RenderDatabaseEnvironmentPostProcessor.PROPERTY_SOURCE))
                .isFalse();
        assertThat(environment.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://localhost:5432/finance_tracker");
    }

    @Test
    void isRegisteredForStartup() throws Exception {
        String factories = new ClassPathResource("META-INF/spring.factories").getContentAsString(StandardCharsets.UTF_8);
        assertThat(factories).contains(RenderDatabaseEnvironmentPostProcessor.class.getName());
    }

    private static ConfigurableEnvironment environment(Map<String, Object> properties) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", properties));
        return environment;
    }
}
