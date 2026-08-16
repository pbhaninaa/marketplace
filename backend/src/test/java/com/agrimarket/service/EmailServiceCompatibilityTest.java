package com.agrimarket.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class EmailServiceCompatibilityTest {

    @Test
    void purposeAwareSendDelegatesToLegacyImplementationByDefault() {
        AtomicBoolean called = new AtomicBoolean();
        EmailService legacyService = (to, subject, plain, html) -> called.set(true);

        legacyService.send(EmailPurpose.INFO, "to@example.com", "Subject", "Plain", "<p>HTML</p>");

        assertThat(called).isTrue();
    }
}
