package com.agrimarket.api.dto;

public record BankDetailsResponse(
        String bankName,
        String accountName,
        String accountNumber,
        String branchCode,
        String referenceHint) {}

