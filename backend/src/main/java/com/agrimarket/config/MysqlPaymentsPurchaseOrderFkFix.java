package com.agrimarket.config;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Some existing MySQL schemas have {@code payments.purchase_order_id} constrained to the old {@code purchase_orders}
 * table. This project uses {@code orders} instead, so that FK will break checkout inserts.
 *
 * We drop the FK (if present) during startup when using MySQL.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class MysqlPaymentsPurchaseOrderFkFix implements ApplicationRunner {

    private final DataSource dataSource;
    private final org.springframework.core.env.Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        String url = environment.getProperty("spring.datasource.url", "");
        if (url == null || !url.toLowerCase().contains("mysql")) {
            return;
        }

        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            // Find any FK on payments.purchase_order_id pointing at purchase_orders.
            try (ResultSet rs = st.executeQuery(
                    """
                    SELECT CONSTRAINT_NAME
                    FROM information_schema.KEY_COLUMN_USAGE
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = 'payments'
                      AND COLUMN_NAME = 'purchase_order_id'
                      AND REFERENCED_TABLE_NAME = 'purchase_orders'
                    """)) {
                while (rs.next()) {
                    String fkName = rs.getString(1);
                    if (fkName == null || fkName.isBlank()) continue;
                    try {
                        st.execute("ALTER TABLE payments DROP FOREIGN KEY " + fkName);
                        log.info("Dropped MySQL FK payments.purchase_order_id -> purchase_orders ({})", fkName);
                    } catch (Exception dropEx) {
                        log.warn("Could not drop FK {} on payments.purchase_order_id: {}", fkName, dropEx.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not inspect/drop payments.purchase_order_id FK: {}", e.getMessage());
        }
    }
}

