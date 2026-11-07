package com.agrimarket.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The aws / azure profiles must run with the full prod configuration and validation. */
class CloudProfilesConfigTest {

    @ParameterizedTest
    @ValueSource(strings = {"aws", "azure"})
    void cloudProfileGroupActivatesProd(String profile) throws IOException {
        Properties base = load("application.properties");
        assertThat(base.getProperty("spring.profiles.group." + profile)).isEqualTo("prod");
    }

    @ParameterizedTest
    @ValueSource(strings = {"aws", "azure"})
    void cloudProfileFileExistsAndAvoidsInvalidInclude(String profile) throws IOException {
        Properties props = load("application-" + profile + ".properties");
        // spring.profiles.include is rejected by Spring Boot in profile-specific files.
        assertThat(props.getProperty("spring.profiles.include")).isNull();
        assertThat(props.getProperty("spring.profiles.active")).isNull();
        assertThat(props.getProperty("server.shutdown")).isEqualTo("graceful");
        assertThat(DeployedProfileValidator.DEPLOYED_PROFILES).contains(profile);
    }

    private static Properties load(String name) throws IOException {
        Properties props = new Properties();
        try (InputStream in = CloudProfilesConfigTest.class.getClassLoader().getResourceAsStream(name)) {
            assertThat(in).as(name + " on classpath").isNotNull();
            props.load(in);
        }
        return props;
    }
}
