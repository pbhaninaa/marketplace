package com.agrimarket.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class ProviderTrialDataMigrationTest {

    @Test
    void backfillsMissingTrialDatesFromCreatedAt_idempotently_withoutTouchingExisting() throws Exception {
        DriverManagerDataSource dataSource =
                new DriverManagerDataSource("jdbc:h2:mem:provider_trial_migration;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute(
                """
                CREATE TABLE providers (
                  id BIGINT PRIMARY KEY,
                  created_at TIMESTAMP NOT NULL,
                  trial_started_at TIMESTAMP,
                  trial_ends_at TIMESTAMP
                )
                """);
        jdbc.execute(
                """
                CREATE TABLE users (
                  id BIGINT PRIMARY KEY,
                  provider_id BIGINT,
                  created_at TIMESTAMP NOT NULL
                )
                """);

        Instant recent = Instant.parse("2026-07-01T10:00:00Z");
        Instant old = Instant.parse("2025-01-01T10:00:00Z");
        Instant presetStart = Instant.parse("2026-06-01T00:00:00Z");
        Instant presetEnd = Instant.parse("2026-07-01T00:00:00Z");

        jdbc.update(
                "INSERT INTO providers(id, created_at, trial_started_at, trial_ends_at) VALUES (?,?,?,?)",
                1L,
                Timestamp.from(recent),
                null,
                null);
        jdbc.update(
                "INSERT INTO providers(id, created_at, trial_started_at, trial_ends_at) VALUES (?,?,?,?)",
                2L,
                Timestamp.from(old),
                null,
                null);
        jdbc.update(
                "INSERT INTO providers(id, created_at, trial_started_at, trial_ends_at) VALUES (?,?,?,?)",
                3L,
                Timestamp.from(recent),
                Timestamp.from(presetStart),
                Timestamp.from(presetEnd));

        ProviderTrialDataMigration migration = new ProviderTrialDataMigration(dataSource, jdbc);
        migration.run(null);
        migration.run(null);

        Map<String, Object> recentRow = jdbc.queryForMap("SELECT * FROM providers WHERE id = 1");
        assertThat(toInstant(recentRow.get("TRIAL_STARTED_AT"))).isEqualTo(recent);
        assertThat(toInstant(recentRow.get("TRIAL_ENDS_AT")))
                .isEqualTo(recent.plus(30, ChronoUnit.DAYS));

        Map<String, Object> oldRow = jdbc.queryForMap("SELECT * FROM providers WHERE id = 2");
        assertThat(toInstant(oldRow.get("TRIAL_STARTED_AT"))).isEqualTo(old);
        assertThat(toInstant(oldRow.get("TRIAL_ENDS_AT"))).isEqualTo(old.plus(30, ChronoUnit.DAYS));
        // Old account trial end is in the past relative to 2026 — no fresh 30 days.
        assertThat(toInstant(oldRow.get("TRIAL_ENDS_AT")).isBefore(Instant.parse("2026-07-17T00:00:00Z")))
                .isTrue();

        Map<String, Object> presetRow = jdbc.queryForMap("SELECT * FROM providers WHERE id = 3");
        assertThat(toInstant(presetRow.get("TRIAL_STARTED_AT"))).isEqualTo(presetStart);
        assertThat(toInstant(presetRow.get("TRIAL_ENDS_AT"))).isEqualTo(presetEnd);
    }

    private static Instant toInstant(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        throw new IllegalArgumentException("Unexpected timestamp type: " + value);
    }
}
