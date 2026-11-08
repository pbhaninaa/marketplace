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

    private static PeachProperties configuredPeach(boolean sandbox) {
        PeachProperties peach = new PeachProperties();
        ReflectionTestUtils.setField(peach, "enabled", true);
        ReflectionTestUtils.setField(peach, "sandbox", sandbox);
        ReflectionTestUtils.setField(peach, "clientId", "c");
        ReflectionTestUtils.setField(peach, "clientSecret", "s");
        ReflectionTestUtils.setField(peach, "merchantId", "m");
        ReflectionTestUtils.setField(peach, "entityId", "e");
        ReflectionTestUtils.setField(peach, "secretToken", "t");
        return peach;
    }

    private static PayFastProperties configuredPayFast(boolean sandbox) {
        PayFastProperties payFast = new PayFastProperties();
        payFast.setEnabled(true);
        payFast.setSandbox(sandbox);
        payFast.setMerchantId("12345678");
        payFast.setMerchantKey("realmerchantkey");
        payFast.setPassphrase("a-long-passphrase");
        payFast.setPublicBaseUrl("https://api.example.com");
        return payFast;
    }

    private static DeployedProfileValidator validatorFor(
            String profile, PeachProperties peach, PayFastProperties payFast) {
        EmailProperties email = enabledEmail();
        email.setDomain("mail.example.com");
        MockEnvironment environment = new MockEnvironment()
                .withProperty("app.jwt.secret", "a-secure-test-secret-that-is-over-32-characters")
                .withProperty("PROD_CORS_ORIGINS", "https://app.example.com")
                .withProperty("PUBLIC_APP_BASE_URL", "https://app.example.com");
        environment.setActiveProfiles(profile);
        return new DeployedProfileValidator(environment, email, peach, payFast);
    }

    @ParameterizedTest
    @ValueSource(strings = {"uat", "prod", "aws", "azure"})
    void deployedProfilesRefuseSandboxGateways(String profile) {
        assertThatIllegalStateException()
                .isThrownBy(() -> validatorFor(profile, new PeachProperties(), configuredPayFast(true)).validate())
                .withMessageContaining("PAYFAST_SANDBOX");
        assertThatIllegalStateException()
                .isThrownBy(() -> validatorFor(profile, configuredPeach(true), new PayFastProperties()).validate())
                .withMessageContaining("PEACH_SANDBOX");
        assertThatNoException()
                .isThrownBy(() -> validatorFor(profile, configuredPeach(false), configuredPayFast(false)).validate());
    }

    @ParameterizedTest
    @ValueSource(strings = {"uat", "prod"})
    void deployedProfilesRefuseThePublicSandboxMerchant(String profile) {
        PayFastProperties demo = configuredPayFast(false);
        demo.setMerchantId("10000100");
        assertThatIllegalStateException()
                .isThrownBy(() -> validatorFor(profile, new PeachProperties(), demo).validate())
                .withMessageContaining("PAYFAST_MERCHANT_ID")
                .withMessageContaining("10000100");
    }

    @Test
    void sandboxIsNotCheckedOutsideDeployedProfiles() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("sit");
        PayFastProperties demo = configuredPayFast(true);
        demo.setMerchantId("10000100");
        assertThatNoException()
                .isThrownBy(() -> new DeployedProfileValidator(environment, enabledEmail(), configuredPeach(true), demo)
                        .validate());
    }

    @Test
    void enabledPayFastNeedsCredentialsPassphraseAndHttpsNotifyBase() {
        PayFastProperties noPassphrase = configuredPayFast(false);
        noPassphrase.setPassphrase("");
        assertThatIllegalStateException()
                .isThrownBy(() -> validatorFor("prod", new PeachProperties(), noPassphrase).validate())
                .withMessageContaining("PAYFAST_PASSPHRASE");

        PayFastProperties noKey = configuredPayFast(true);
        noKey.setMerchantKey(" ");
        assertThatIllegalStateException()
                .isThrownBy(() -> validatorFor("uat", new PeachProperties(), noKey).validate())
                .withMessageContaining("PAYFAST_MERCHANT_KEY");

        PayFastProperties http = configuredPayFast(false);
        http.setPublicBaseUrl("http://api.example.com");
        assertThatIllegalStateException()
                .isThrownBy(() -> validatorFor("prod", new PeachProperties(), http).validate())
                .withMessageContaining("PAYFAST_PUBLIC_BASE_URL");

        PayFastProperties missingBase = configuredPayFast(true);
        missingBase.setPublicBaseUrl("");
        assertThatIllegalStateException()
                .isThrownBy(() -> validatorFor("uat", new PeachProperties(), missingBase).validate())
                .withMessageContaining("PAYFAST_PUBLIC_BASE_URL");

        // Disabled PayFast with nothing set is fine.
        assertThatNoException()
                .isThrownBy(() -> validatorFor("prod", new PeachProperties(), new PayFastProperties()).validate());
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
    void rejectsExposedSignupTestCodesOnDeployedProfiles() {
        EmailProperties email = enabledEmail();
        email.setDomain("mail.example.com");
        DeployedProfileValidator v = validator(email);
        ((MockEnvironment) ReflectionTestUtils.getField(v, "environment"))
                .setProperty("app.signup.expose-test-code", "true");
        assertThatIllegalStateException()
                .isThrownBy(v::validate)
                .withMessageContaining("SIGNUP_EXPOSE_TEST_CODE");
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
