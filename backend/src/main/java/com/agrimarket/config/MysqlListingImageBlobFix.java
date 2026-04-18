package com.agrimarket.config;

import java.sql.Connection;
import java.sql.Statement;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Hibernate {@code ddl-auto=update} often does not widen an existing MySQL {@code TINYBLOB} column. The
 * {@code listing_images.data} column must store full photo binaries — run ALTER once at startup when using MySQL.
 */
@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class MysqlListingImageBlobFix implements ApplicationRunner {

    private final DataSource dataSource;
    private final org.springframework.core.env.Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        String url = environment.getProperty("spring.datasource.url", "");
        if (url == null || !url.toLowerCase().contains("mysql")) {
            return;
        }
        try (Connection c = dataSource.getConnection();
                Statement st = c.createStatement()) {
            st.execute("ALTER TABLE listing_images MODIFY COLUMN data LONGBLOB NOT NULL");
            log.info("Ensured listing_images.data is LONGBLOB (MySQL)");
        } catch (Exception e) {
            log.warn("Could not ALTER listing_images.data to LONGBLOB (may already be correct): {}", e.getMessage());
        }
    }
}
