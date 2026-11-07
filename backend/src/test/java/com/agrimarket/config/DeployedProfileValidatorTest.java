package com.agrimarket.config;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNoException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

    @ParameterizedTest
    @ValueSource(strings = {"aws", "azure"})
    void cloudProfilesAreValidatedLikeProd(String profile) {
        EmailProperties email = enabledEmail();
        email.setDomain("mail.example.com");
        MockEnvironment environment = new MockEnvironment()
                .withProperty("app.jwt.secret", "too-short")
                .withProperty("PROD_CORS_ORIGINS", "https://app.example.com")
                .withProperty("PUBLIC_APP_BASE_URL", "https://app.example.com");
        environment.setActiveProfiles(profile);
        DeployedProfileValidator validator =
                new DeployedProfileValidator(environment, email, new PeachProperties());

        assertThatIllegalStateException()
                .isThrownBy(validator::validate)
                .withMessageContaining("APP_JWT_SECRET");

        environment.setProperty("app.jwt.secret", "a-secure-test-secret-that-is-over-32-characters");
        assertThatNoException().isThrownBy(validator::validate);
    }

    @Test
    void localProfileIsNotValidated() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        assertThatNoException()
                .isThrownBy(() -> new DeployedProfileValidator(
                                environment, enabledEmail(), new PeachProperties())
                        .validate());
    }

    @Test
    void rejectsSignupEmailVerificationWithoutEmailSending() {
        EmailProperties disabled = new EmailProperties();
        disabled.setEnabled(false);

        assertThatIllegalStateException()
                .isThrownBy(() -> validator(disabled).validate())
                .withMessageContaining("SIGNUP_REQUIRE_EMAIL_VERIFICATION");
    }

    @Test
    void rejectsDisabledRateLimitsAndBadProxyHops() {
        EmailProperties email = enabledEmail();
        email.setDomain("mail.example.com");

        DeployedProfileValidator noLimits = validator(email);
        ((MockEnvironment) ReflectionTestUtils.getField(noLimits, "environment"))
                .setProperty("app.security.rate-limit.enabled", "false");
        assertThatIllegalStateException()
                .isThrownBy(noLimits::validate)
                .withMessageContaining("RATE_LIMIT_ENABLED");

        DeployedProfileValidator badHops = validator(email);
        ((MockEnvironment) ReflectionTestUtils.getField(badHops, "environment"))
                .setProperty("app.security.trusted-proxy-hops", "-1");
        assertThatIllegalStateException()
                .isThrownBy(badHops::validate)
                .withMessageContaining("TRUSTED_PROXY_HOPS");
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
