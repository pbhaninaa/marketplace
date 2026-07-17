package com.agrimarket.config;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNoException;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

class DeployedProfileValidatorTest {

    @Test
    void acceptsApiKeyAndEmailDomain() {
        EmailProperties properties = enabledEmail();
        properties.setDomain("mail.example.com");

        assertThatNoException().isThrownBy(() -> validator(properties).validate());
    }

    @Test
    void acceptsEmailFromFallback() {
        EmailProperties properties = enabledEmail();
        properties.setFrom("no-reply@example.com");

        assertThatNoException().isThrownBy(() -> validator(properties).validate());
    }

    @Test
    void rejectsMissingSenderAndInvalidDomain() {
        EmailProperties properties = enabledEmail();

        assertThatIllegalStateException()
                .isThrownBy(() -> validator(properties).validate())
                .withMessageContaining("EMAIL_DOMAIN or EMAIL_FROM");

        properties.setDomain("https://example.com");
        assertThatIllegalStateException()
                .isThrownBy(() -> validator(properties).validate())
                .withMessageContaining("EMAIL_DOMAIN must be a domain");
    }

    @Test
    void rejectsEnabledButIncompletePeachConfiguration() {
        PeachProperties peach = new PeachProperties();
        ReflectionTestUtils.setField(peach, "enabled", true);
        EmailProperties email = enabledEmail();
        email.setDomain("mail.example.com");

        assertThatIllegalStateException()
                .isThrownBy(() -> validator(email, peach).validate())
                .withMessageContaining("PEACH_ENABLED=true requires");
    }

    private static EmailProperties enabledEmail() {
        EmailProperties properties = new EmailProperties();
        properties.setEnabled(true);
        properties.setSendgridApiKey("test-api-key");
        return properties;
    }

    private static DeployedProfileValidator validator(EmailProperties properties) {
        return validator(properties, new PeachProperties());
    }

    private static DeployedProfileValidator validator(
            EmailProperties properties, PeachProperties peachProperties) {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("app.jwt.secret", "a-secure-test-secret-that-is-over-32-characters")
                .withProperty("UAT_CORS_ORIGINS", "https://uat.example.com")
                .withProperty("PUBLIC_APP_BASE_URL", "https://uat.example.com");
        environment.setActiveProfiles("uat");
        return new DeployedProfileValidator(environment, properties, peachProperties);
    }
}
