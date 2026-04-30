package com.agrimarket.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.bank")
public record BankDetailsProperties(
        String bankName,
        String accountName,
        String accountNumber,
        String branchCode,
        String referenceHint) {}

