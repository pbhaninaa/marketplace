package com.agrimarket.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class PaymentMethodDataMigrationTest {

    @Test
    void expandsBothKeepsEftAndMapsCardIdempotently() throws Exception {
        DriverManagerDataSource dataSource =
                new DriverManagerDataSource("jdbc:h2:mem:payment_migration;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute(
                "CREATE TABLE provider_accepted_payment_methods (provider_id BIGINT NOT NULL, payment_method VARCHAR(20) NOT NULL)");
        jdbc.execute("CREATE TABLE payments (id BIGINT PRIMARY KEY, method VARCHAR(20) NOT NULL)");
        jdbc.update(
                "INSERT INTO provider_accepted_payment_methods(provider_id, payment_method) VALUES "
                        + "(1, 'BOTH'), (2, 'EFT'), (3, 'CARD'), (3, 'CASH')");
        jdbc.update("INSERT INTO payments(id, method) VALUES (1, 'CARD'), (2, 'EFT')");

        PaymentMethodDataMigration migration = new PaymentMethodDataMigration(dataSource, jdbc);
        migration.run(null);
        migration.run(null);

        List<Map<String, Object>> providerMethods = jdbc.queryForList(
                "SELECT provider_id, payment_method FROM provider_accepted_payment_methods "
                        + "ORDER BY provider_id, payment_method");
        assertThat(providerMethods)
                .extracting(row -> row.get("PAYMENT_METHOD"))
                .containsExactly("CASH", "EFT", "EFT", "CASH", "PEACH");
        assertThat(jdbc.queryForObject("SELECT method FROM payments WHERE id = 1", String.class))
                .isEqualTo("PEACH");
        assertThat(jdbc.queryForObject("SELECT method FROM payments WHERE id = 2", String.class))
                .isEqualTo("EFT");
    }
}
