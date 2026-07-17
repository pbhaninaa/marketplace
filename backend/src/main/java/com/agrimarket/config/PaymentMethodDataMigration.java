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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Idempotently upgrades legacy payment-method rows after Hibernate has created any new columns.
 * This runs on clean and upgraded databases, so deployment never depends on a manual SQL step.
 *
 * <p>Current selectable cart methods are CASH, EFT (manual), and PEACH. Legacy BOTH expands to
 * CASH+EFT; historical CARD provider rows and payment records map to PEACH.
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
            int removed =
                    jdbcTemplate.update(
                            "DELETE FROM provider_accepted_payment_methods WHERE payment_method IN ('BOTH', 'CARD')");
            if (removed > 0) {
                log.info("Normalized {} legacy provider payment-method rows (BOTH/CARD)", removed);
            }
        }

        // CARD was a historical top-level value but is not a current PaymentMethod enum member.
        if (tableExists("payments")) {
            int updated = jdbcTemplate.update("UPDATE payments SET method = 'PEACH' WHERE method = 'CARD'");
            if (updated > 0) {
                log.info("Normalized {} historical CARD payment records to PEACH", updated);
            }
        }
    }

    private void expandBothToCashAndEft() {
        for (String method : new String[] {"CASH", "EFT"}) {
            jdbcTemplate.update(
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
        jdbcTemplate.update(
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
