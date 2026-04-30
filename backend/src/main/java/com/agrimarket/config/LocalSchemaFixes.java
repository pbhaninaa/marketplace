package com.agrimarket.config;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.boot.context.event.ApplicationReadyEvent;

/**
 * Local-only "safety" schema adjustments for older MySQL schemas that Hibernate's ddl-auto=update
 * can't evolve (e.g. MySQL ENUM columns).
 */
@Component
@Profile("local")
@RequiredArgsConstructor
public class LocalSchemaFixes {

    private final JdbcTemplate jdbcTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void apply() {
        fixSubscriptionStatusColumn();
    }

    private void fixSubscriptionStatusColumn() {
        try {
            Map<String, Object> row = jdbcTemplate.queryForMap(
                    """
                    SELECT COLUMN_TYPE
                    FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = 'subscriptions'
                      AND COLUMN_NAME = 'status'
                    """);
            String columnType = row.get("COLUMN_TYPE") == null ? "" : row.get("COLUMN_TYPE").toString();
            // Older schemas used ENUM which doesn't include newer values like PENDING_VERIFICATION.
            if (columnType.toLowerCase().startsWith("enum(")) {
                jdbcTemplate.execute("ALTER TABLE subscriptions MODIFY COLUMN status VARCHAR(32) NOT NULL");
            }
        } catch (Exception ignored) {
            // Best-effort local fix; don't block startup.
        }
    }
}

