package com.agrimarket.config;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Idempotently upgrades legacy payment-method rows after Hibernate has created any new columns.
 * This runs on clean and upgraded databases, so deployment never depends on a manual SQL step.
 *
 * <p>Current selectable cart methods are CASH, EFT (manual), and PEACH. Legacy BOTH expands to
 * CASH+EFT; historical CARD provider rows and payment records map to PEACH.
 *
 * <p>On a fresh schema Hibernate 6.5+ may create a native enum column (e.g. H2) that only admits the
 * current enum values. Such a column cannot hold BOTH/CARD, and some databases reject even comparing it
 * with those literals, so a rejected legacy statement simply means there is nothing to migrate.
 */
@Component
@Order(2)
public class PaymentMethodDataMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PaymentMethodDataMigration.class);

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    public PaymentMethodDataMigration(DataSource dataSource, JdbcTemplate jdbcTemplate) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (tableExists("provider_accepted_payment_methods")) {
            expandBothToCashAndEft();
            mapCardProviderMethodToPeach();
            int removed = legacyUpdate(
                    "DELETE FROM provider_accepted_payment_methods WHERE payment_method IN ('BOTH', 'CARD')");
            if (removed > 0) {
                log.info("Normalized {} legacy provider payment-method rows (BOTH/CARD)", removed);
            }
        }

        // CARD was a historical top-level value but is not a current PaymentMethod enum member.
        if (tableExists("payments")) {
            int updated = legacyUpdate("UPDATE payments SET method = 'PEACH' WHERE method = 'CARD'");
            if (updated > 0) {
                log.info("Normalized {} historical CARD payment records to PEACH", updated);
            }
        }
    }

    private void expandBothToCashAndEft() {
        for (String method : new String[] {"CASH", "EFT"}) {
            legacyUpdate(
                    """
                    INSERT INTO provider_accepted_payment_methods (provider_id, payment_method)
                    SELECT DISTINCT legacy.provider_id, ?
                    FROM provider_accepted_payment_methods legacy
                    WHERE legacy.payment_method = 'BOTH'
                      AND NOT EXISTS (
                        SELECT 1
                        FROM provider_accepted_payment_methods current_method
                        WHERE current_method.provider_id = legacy.provider_id
                          AND current_method.payment_method = ?
                      )
                    """,
                    method,
                    method);
        }
    }

    private void mapCardProviderMethodToPeach() {
        legacyUpdate(
                """
                INSERT INTO provider_accepted_payment_methods (provider_id, payment_method)
                SELECT DISTINCT legacy.provider_id, 'PEACH'
                FROM provider_accepted_payment_methods legacy
                WHERE legacy.payment_method = 'CARD'
                  AND NOT EXISTS (
                    SELECT 1
                    FROM provider_accepted_payment_methods current_method
                    WHERE current_method.provider_id = legacy.provider_id
                      AND current_method.payment_method = 'PEACH'
                  )
                """);
    }

    /** Runs a statement that references legacy values; 0 when the column type cannot hold them. */
    private int legacyUpdate(String sql, Object... params) {
        try {
            return jdbcTemplate.update(sql, params);
        } catch (DataIntegrityViolationException ex) {
            log.debug("Skipping legacy payment-method migration step (column rejects legacy values): {}",
                    ex.getMostSpecificCause().getMessage());
            return 0;
        }
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
}
