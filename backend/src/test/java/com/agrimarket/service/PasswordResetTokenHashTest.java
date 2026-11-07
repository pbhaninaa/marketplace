package com.agrimarket.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Reset tokens are stored as SHA-256 hex, never as the raw value that goes in the email link. */
class PasswordResetTokenHashTest {

    @Test
    void hashIsStableHexAndNeverTheRawToken() {
        String raw = "q3Zr1y8tM2xV0b5nK7wP4sL6dF9gH1jA2cE3uI5oR7e";
        String hashed = PasswordResetService.hashToken(raw);

        assertThat(hashed).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(hashed).isNotEqualTo(raw);
        assertThat(PasswordResetService.hashToken(raw)).isEqualTo(hashed);
        assertThat(PasswordResetService.hashToken(raw + "x")).isNotEqualTo(hashed);
    }
}
