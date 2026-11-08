package com.agrimarket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agrimarket.api.error.ApiException;
import com.agrimarket.config.AppProperties;
import com.agrimarket.config.AuthSecurityProperties;
import com.agrimarket.domain.ClientOtpChallenge;
import com.agrimarket.repo.ClientOtpChallengeRepository;
import com.agrimarket.security.JwtService;
import com.agrimarket.security.RateLimiter;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

class ClientOtpServiceTest {

    private static final Pattern CODE = Pattern.compile("(\\d{6})");

    private final ClientOtpChallengeRepository repo = mock(ClientOtpChallengeRepository.class);
    private final EmailService email = mock(EmailService.class);
    private final List<ClientOtpChallenge> rows = new ArrayList<>();
    private ClientOtpService service;

    @BeforeEach
    void setUp() {
        AppProperties app = new AppProperties(
                "TEST", null, null, null, new AppProperties.Otp(10, 5), null, false);
        service = new ClientOtpService(
                app,
                repo,
                new BCryptPasswordEncoder(4),
                mock(JwtService.class),
                email,
                new MockEnvironment(), // no dev profile: codes are emailed
                new RateLimiter(Clock.systemUTC(), true),
                new AuthSecurityProperties());

        when(repo.save(any(ClientOtpChallenge.class))).thenAnswer(inv -> {
            ClientOtpChallenge c = inv.getArgument(0);
            if (!rows.contains(c)) rows.add(c);
            return c;
        });
        when(repo.findTopByTargetAndPurposeOrderByCreatedAtDesc(anyString(), anyString())).thenAnswer(inv -> {
            String target = inv.getArgument(0);
            String purpose = inv.getArgument(1);
            ClientOtpChallenge last = null;
            for (ClientOtpChallenge c : rows) {
                if (c.getTarget().equals(target) && purpose.equals(c.getPurpose())) last = c;
            }
            return Optional.ofNullable(last);
        });
        doAnswer(inv -> {
            String target = inv.getArgument(0);
            String purpose = inv.getArgument(1);
            rows.removeIf(c -> c.getTarget().equals(target) && purpose.equals(c.getPurpose()));
            return null;
        }).when(repo).deleteByTargetAndPurpose(anyString(), anyString());
    }

    private String lastEmailedCode() {
        ArgumentCaptor<String> plain = ArgumentCaptor.forClass(String.class);
        verify(email, org.mockito.Mockito.atLeastOnce())
                .send(any(EmailPurpose.class), anyString(), anyString(), plain.capture(), anyString());
        Matcher m = CODE.matcher(plain.getValue());
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    @Test
    void signupCodeIsCheckedWithoutConsumingUntilTheAccountExists() {
        service.requestSignupCode(" Owner@Farm.test ");
        String code = lastEmailedCode();

        assertThat(service.verifySignupCode("owner@farm.test", code)).isTrue();
        // Still usable: a form that failed on another field can be resubmitted.
        assertThat(service.verifySignupCode("OWNER@farm.test", code)).isTrue();

        service.consumeSignupCode("owner@farm.test");
        assertThat(service.verifySignupCode("owner@farm.test", code)).isFalse();
    }

    @Test
    void signupCodeStopsWorkingAfterFiveWrongGuesses() {
        service.requestSignupCode("owner@farm.test");
        String code = lastEmailedCode();
        String wrong = code.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 5; i++) {
            assertThat(service.verifySignupCode("owner@farm.test", wrong)).isFalse();
        }
        assertThat(rows.get(0).getAttempts()).isEqualTo(5);
        // Even the right code is refused now.
        assertThat(service.verifySignupCode("owner@farm.test", code)).isFalse();
    }

    @Test
    void codesForOnePurposeDoNotWorkForTheOther() {
        service.requestOtp("owner@farm.test");
        String clientCode = lastEmailedCode();
        assertThat(service.verifySignupCode("owner@farm.test", clientCode)).isFalse();
        assertThat(rows).allMatch(c -> ClientOtpService.PURPOSE_CLIENT_LOGIN.equals(c.getPurpose()));
    }

    @Test
    void clientVerifyCountsWrongGuessesAndThenLocks() {
        service.requestOtp("guest@farm.test");
        String code = lastEmailedCode();
        String wrong = code.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> service.verify("guest@farm.test", wrong))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("Invalid or expired code");
        }
        assertThatThrownBy(() -> service.verify("guest@farm.test", code))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("OTP_LOCKED"));
    }

    /** Wrong guesses are saved and then an ApiException is thrown: the transaction must not roll them back. */
    @Test
    void clientVerifyKeepsWrongGuessCountsWhenItThrows() throws Exception {
        Transactional tx = ClientOtpService.class
                .getMethod("verify", String.class, String.class)
                .getAnnotation(Transactional.class);
        assertThat(tx).isNotNull();
        assertThat(tx.noRollbackFor()).contains(ApiException.class);
    }

    @Test
    void sendIsCappedPerEmailAndPurpose() {
        for (int i = 0; i < 3; i++) service.requestSignupCode("b@farm.test");
        service.requestSignupCode("B@farm.test "); // over the cap: silently not sent
        verify(email, times(3)).send(any(EmailPurpose.class), eq("b@farm.test"), anyString(), anyString(), anyString());

        // A different purpose has its own allowance; support resends are not capped.
        service.requestOtp("b@farm.test");
        for (int i = 0; i < 4; i++) service.resendBySupport("b@farm.test");
        verify(email, times(8)).send(any(EmailPurpose.class), eq("b@farm.test"), anyString(), anyString(), anyString());
    }

    @Test
    void blankInputsFail() {
        assertThat(service.verifySignupCode("", "123456")).isFalse();
        assertThat(service.verifySignupCode("a@farm.test", " ")).isFalse();
        service.requestSignupCode("  ");
        assertThat(rows).isEmpty();
    }

    private ClientOtpService serviceWith(MockEnvironment env) {
        AppProperties app = new AppProperties(
                "TEST", null, null, null, new AppProperties.Otp(10, 5), null, false);
        return new ClientOtpService(
                app,
                repo,
                new BCryptPasswordEncoder(4),
                mock(JwtService.class),
                email,
                env,
                new RateLimiter(Clock.systemUTC(), true),
                new AuthSecurityProperties());
    }

    @Test
    void sitReturnsTheSignupCodeToFillInAndSendsNoEmail() {
        MockEnvironment env = new MockEnvironment().withProperty("app.signup.expose-test-code", "true");
        env.setActiveProfiles("sit");
        ClientOtpService sit = serviceWith(env);

        Optional<String> code = sit.requestSignupCode("new@farm.test");

        assertThat(code).isPresent();
        assertThat(code.get()).matches("[0-9]{6}");
        assertThat(sit.verifySignupCode("new@farm.test", code.get())).isTrue();
        verify(email, times(0)).send(any(EmailPurpose.class), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void uatAndProdNeverReturnTheCodeEvenIfTheFlagIsSet() {
        for (String profile : List.of("uat", "prod")) {
            MockEnvironment env = new MockEnvironment().withProperty("app.signup.expose-test-code", "true");
            env.setActiveProfiles(profile);
            assertThat(serviceWith(env).requestSignupCode(profile + "@farm.test")).isEmpty();
            verify(email).send(any(EmailPurpose.class), eq(profile + "@farm.test"), anyString(), anyString(), anyString());
        }
    }

    @Test
    void codeIsNotReturnedWithoutTheFlag() {
        assertThat(service.requestSignupCode("plain@farm.test")).isEmpty();
    }
}
