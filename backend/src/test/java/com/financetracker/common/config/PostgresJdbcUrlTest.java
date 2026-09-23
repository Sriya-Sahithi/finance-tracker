package com.financetracker.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class PostgresJdbcUrlTest {

    @Test
    void convertsRenderConnectionString() {
        Optional<PostgresJdbcUrl.Parsed> parsed = PostgresJdbcUrl.parse(
                "postgresql://finance:secret@dpg-abc-a:5432/finance_tracker");

        assertThat(parsed).isPresent();
        assertThat(parsed.orElseThrow().jdbcUrl())
                .isEqualTo("jdbc:postgresql://dpg-abc-a:5432/finance_tracker");
        assertThat(parsed.orElseThrow().username()).isEqualTo("finance");
        assertThat(parsed.orElseThrow().password()).isEqualTo("secret");
    }

    @Test
    void acceptsPostgresSchemeAndDecodesPassword() {
        PostgresJdbcUrl.Parsed parsed = PostgresJdbcUrl.parse(
                "postgres://app_user:p%40ss%3Aword@db.internal/finance_tracker?sslmode=require").orElseThrow();

        assertThat(parsed.jdbcUrl())
                .isEqualTo("jdbc:postgresql://db.internal/finance_tracker?sslmode=require");
        assertThat(parsed.username()).isEqualTo("app_user");
        assertThat(parsed.password()).isEqualTo("p@ss:word");
    }

    @Test
    void leavesJdbcUrlsAlone() {
        assertThat(PostgresJdbcUrl.parse("jdbc:postgresql://localhost:5432/finance_tracker")).isEmpty();
        assertThat(PostgresJdbcUrl.parse("  ")).isEmpty();
        assertThat(PostgresJdbcUrl.parse(null)).isEmpty();
    }

    @Test
    void rejectsOtherSchemesWithoutEchoingTheUrl() {
        assertThatThrownBy(() -> PostgresJdbcUrl.parse("mysql://user:secret@localhost/db"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jdbc:postgresql")
                .hasMessageNotContaining("secret");
    }
}
