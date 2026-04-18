package com.agrimarket.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

@Configuration
@RequiredArgsConstructor
public class DataInitializer {

    private final AppProperties appProperties;

    @Bean
    @Order(0)
    CommandLineRunner seedOnSit() {
        return args -> {
            // Demo data seeding has been removed. Keep only the default-admin seeder.
            if (appProperties.seedDemoData()) {
                // Intentionally no-op.
                return;
            }
        };
    }
}
