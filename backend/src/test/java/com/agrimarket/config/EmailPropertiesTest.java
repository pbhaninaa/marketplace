package com.agrimarket.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.agrimarket.service.EmailPurpose;
import org.junit.jupiter.api.Test;

class EmailPropertiesTest {

    @Test
    void resolvesPurposeAddressFromConfiguredDomain() {
        EmailProperties properties = new EmailProperties();
        properties.setDomain(" Example.COM ");
        properties.setFrom("fallback@example.net");

        assertThat(properties.resolveFrom(EmailPurpose.INFO)).isEqualTo("info@example.com");
        assertThat(properties.resolveFrom(EmailPurpose.SUPPORT)).isEqualTo("support@example.com");
        assertThat(properties.resolveFrom(EmailPurpose.SECURITY)).isEqualTo("security@example.com");
        assertThat(properties.resolveFrom(EmailPurpose.BILLING)).isEqualTo("billing@example.com");
        assertThat(properties.resolveFrom(EmailPurpose.NO_REPLY)).isEqualTo("no-reply@example.com");
    }

    @Test
    void fallsBackToEmailFromWhenDomainIsUnset() {
        EmailProperties properties = new EmailProperties();
        properties.setFrom(" fallback@example.net ");

        assertThat(properties.resolveFrom(EmailPurpose.SECURITY)).isEqualTo("fallback@example.net");
        assertThat(properties.hasConfiguredSender()).isTrue();
    }

    @Test
    void rejectsDomainContainingSchemePathOrAddress() {
        EmailProperties properties = new EmailProperties();

        properties.setDomain("https://example.com");
        assertThat(properties.hasValidConfiguredDomain()).isFalse();

        properties.setDomain("example.com/path");
        assertThat(properties.hasValidConfiguredDomain()).isFalse();

        properties.setDomain("info@example.com");
        assertThat(properties.hasValidConfiguredDomain()).isFalse();
    }
}
