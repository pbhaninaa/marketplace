package com.agrimarket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AgriMarketplaceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgriMarketplaceApplication.class, args);
    }
}
