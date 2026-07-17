package com.agrimarket.config;

import com.agrimarket.service.ProviderTrialSupport;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Idempotent backfill of provider free-trial dates after Hibernate has added the new columns.
 * Anchors trial start/end to provider {@code created_at} (fallback: earliest owner account
 * {@code created_at}). Never overwrites dates that are already set. Does not touch paid
 * subscriptions or re-issue trials for accounts that already have trial fields.
 */
@Component
@Order(3)
public class ProviderTrialDataMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProviderTrialDataMigration.class);

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    public ProviderTrialDataMigration(DataSource dataSource, JdbcTemplate jdbcTemplate) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!tableExists("providers") || !columnExists("providers", "trial_started_at")) {
            return;
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT p.id AS provider_id, p.created_at AS provider_created_at
                FROM providers p
                WHERE p.trial_started_at IS NULL
                  AND p.trial_ends_at IS NULL
                """);

        int updated = 0;
        for (Map<String, Object> row : rows) {
            Long providerId = toLong(firstValue(row, "provider_id", "PROVIDER_ID"));
            Instant anchor = toInstant(firstValue(row, "provider_created_at", "PROVIDER_CREATED_AT"));
            if (anchor == null) {
                anchor = earliestOwnerCreatedAt(providerId);
            }
            if (anchor == null) {
                log.warn("Skipping provider {} trial backfill: no created_at anchor", providerId);
                continue;
            }
            Instant trialEnd = anchor.plus(ProviderTrialSupport.TRIAL_DAYS, ChronoUnit.DAYS);
            int n = jdbcTemplate.update(
                    """
                    UPDATE providers
                    SET trial_started_at = ?, trial_ends_at = ?
                    WHERE id = ?
                      AND trial_started_at IS NULL
                      AND trial_ends_at IS NULL
                    """,
                    Timestamp.from(anchor),
                    Timestamp.from(trialEnd),
                    providerId);
            updated += n;
        }

        if (updated > 0) {
            log.info("Backfilled free-trial dates for {} provider(s)", updated);
        }
    }

    private Instant earliestOwnerCreatedAt(Long providerId) {
        List<Map<String, Object>> owners = jdbcTemplate.queryForList(
                """
                SELECT created_at
                FROM users
                WHERE provider_id = ?
                ORDER BY created_at ASC
                """,
                providerId);
        if (owners.isEmpty()) {
            return null;
        }
        return toInstant(firstValue(owners.get(0), "created_at", "CREATED_AT"));
    }

    private static Object firstValue(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            if (row.containsKey(key) && row.get(key) != null) {
                return row.get(key);
            }
        }
        return null;
    }

    private static Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    private static Instant toInstant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof java.util.Date date) {
            return date.toInstant();
        }
        return null;
    }

    private boolean tableExists(String tableName) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            try (ResultSet tables = metadata.getTables(connection.getCatalog(), null, tableName, new String[] {"TABLE"})) {
                if (tables.next()) {
                    return true;
                }
            }
            try (ResultSet tables =
                    metadata.getTables(connection.getCatalog(), null, tableName.toUpperCase(), new String[] {"TABLE"})) {
                return tables.next();
            }
        }
    }

    private boolean columnExists(String tableName, String columnName) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            if (columnExists(metadata, connection.getCatalog(), tableName, columnName)) {
                return true;
            }
            return columnExists(metadata, connection.getCatalog(), tableName.toUpperCase(), columnName.toUpperCase());
        }
    }

    private static boolean columnExists(
            DatabaseMetaData metadata, String catalog, String tableName, String columnName) throws Exception {
        try (ResultSet columns = metadata.getColumns(catalog, null, tableName, columnName)) {
            return columns.next();
        }
    }
}
