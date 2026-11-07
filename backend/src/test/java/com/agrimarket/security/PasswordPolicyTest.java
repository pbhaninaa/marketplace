package com.agrimarket.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agrimarket.api.error.ApiException;
import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    @Test
    void acceptsAReasonablePassword() {
        assertThat(PasswordPolicy.validate("blue-Kettle-42", "thandi@farm.test")).isEmpty();
        // Passwords used by the existing integration tests stay valid.
        assertThat(PasswordPolicy.validate("SecurePass123", "owner-1@integration.test")).isEmpty();
        assertThat(PasswordPolicy.validate("NewSecurePass88!", "reset-user@integration.test")).isEmpty();
    }

    @Test
    void rejectsMissingShortAndOverlongPasswords() {
        assertThat(PasswordPolicy.validate(null, "u@x.test")).contains("Password is required");
        assertThat(PasswordPolicy.validate("   ", "u@x.test")).contains("Password is required");
        assertThat(PasswordPolicy.validate("Ab3$xyz", "u@x.test"))
                .hasValueSatisfying(m -> assertThat(m).contains("at least 8"));
        assertThat(PasswordPolicy.validate("a".repeat(30) + "B".repeat(35), "u@x.test"))
                .hasValueSatisfying(m -> assertThat(m).contains("at most"));
        // 24 four-byte emoji = 96 bytes: under 64 chars but over BCrypt's 72-byte limit.
        assertThat(PasswordPolicy.validate("😀".repeat(24), "u@x.test"))
                .hasValueSatisfying(m -> assertThat(m).contains("at most"));
    }

    @Test
    void rejectsCommonPasswordsIncludingDecoratedOnes() {
        assertThat(PasswordPolicy.validate("password", "u@x.test")).hasValueSatisfying(m -> assertThat(m).contains("too common"));
        assertThat(PasswordPolicy.validate("Password123!", "u@x.test")).hasValueSatisfying(m -> assertThat(m).contains("too common"));
        assertThat(PasswordPolicy.validate("QWERTYUIOP", "u@x.test")).hasValueSatisfying(m -> assertThat(m).contains("too common"));
        assertThat(PasswordPolicy.validate("123456789", "u@x.test")).hasValueSatisfying(m -> assertThat(m).contains("too common"));
        assertThat(PasswordPolicy.validate("Springbok2024", "u@x.test")).hasValueSatisfying(m -> assertThat(m).contains("too common"));
        assertThat(PasswordPolicy.validate("Tractor2026!", "u@x.test")).hasValueSatisfying(m -> assertThat(m).contains("too common"));
    }

    @Test
    void rejectsRepeatedCharacters() {
        assertThat(PasswordPolicy.validate("zzzzzzzzzz", "u@x.test")).hasValueSatisfying(m -> assertThat(m).contains("too simple"));
        assertThat(PasswordPolicy.validate("ababababab", "u@x.test")).hasValueSatisfying(m -> assertThat(m).contains("too simple"));
    }

    @Test
    void rejectsPasswordContainingTheEmailName() {
        assertThat(PasswordPolicy.validate("Thandi#2026x", "thandi@farm.test"))
                .hasValueSatisfying(m -> assertThat(m).contains("email"));
        // Very short email names are not checked (too many false positives).
        assertThat(PasswordPolicy.validate("blue-Kettle-42", "bl@farm.test")).isEmpty();
    }

    @Test
    void requireThrowsBadRequestWithWeakPasswordCode() {
        assertThatThrownBy(() -> PasswordPolicy.require("short", "u@x.test"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("at least 8")
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("WEAK_PASSWORD"));
    }
}
