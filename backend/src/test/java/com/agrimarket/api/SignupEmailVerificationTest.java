package com.agrimarket.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agrimarket.api.dto.ProviderRegisterRequest;
import com.agrimarket.api.error.ApiException;
import com.agrimarket.service.BootstrapService;
import com.agrimarket.service.ClientOtpService;
import com.agrimarket.service.PasswordResetService;
import com.agrimarket.service.ProviderRegistrationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * Provider sign-up must prove ownership of the owner email before the server reveals whether that
 * email already has an account.
 */
class SignupEmailVerificationTest {

    private final BootstrapService bootstrap = mock(BootstrapService.class);
    private final ProviderRegistrationService registration = mock(ProviderRegistrationService.class);
    private final ClientOtpService otp = mock(ClientOtpService.class);

    private PublicRegistrationController controller(boolean requireVerification) {
        return new PublicRegistrationController(
                bootstrap, registration, mock(PasswordResetService.class), otp, requireVerification);
    }

    private static ProviderRegisterRequest request(String email, String code) {
        return new ProviderRegisterRequest(
                "Green Acres", "Fresh produce", "Stellenbosch", null, email, "blue-Kettle-42", null, code);
    }

    @Test
    void wrongOrMissingCodeIsRejectedBeforeAnyEmailCheck() {
        when(otp.verifySignupCode("taken@farm.test", "000000")).thenReturn(false);

        assertThatThrownBy(() -> controller(true).registerProvider(request("taken@farm.test", "000000")))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException api = (ApiException) e;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(api.getCode()).isEqualTo("EMAIL_CODE_INVALID");
                    assertThat(api.getMessage()).contains("verification code");
                });
        // The "email already registered" check lives in ProviderRegistrationService.register: never reached.
        verify(registration, never()).register(any());
    }

    @Test
    void validCodeCreatesTheProviderAndThenUsesTheCodeUp() {
        ProviderRegisterRequest req = request("me@farm.test", "123456");
        when(otp.verifySignupCode("me@farm.test", "123456")).thenReturn(true);

        controller(true).registerProvider(req);

        verify(registration).register(req);
        verify(otp).consumeSignupCode("me@farm.test");
    }

    @Test
    void codeStaysUsableWhenSavingFailsForAnotherReason() {
        ProviderRegisterRequest req = request("me@farm.test", "123456");
        when(otp.verifySignupCode("me@farm.test", "123456")).thenReturn(true);
        doThrow(new ApiException(HttpStatus.BAD_REQUEST, "WEAK_PASSWORD", "too common"))
                .when(registration).register(req);

        assertThatThrownBy(() -> controller(true).registerProvider(req)).isInstanceOf(ApiException.class);
        verify(otp, never()).consumeSignupCode(anyString());
    }

    @Test
    void verificationCanBeSwitchedOffForLocalAndTests() {
        ProviderRegisterRequest req = request("me@farm.test", null);

        controller(false).registerProvider(req);

        verify(registration).register(req);
        verify(otp, never()).verifySignupCode(any(), any());
    }

    @Test
    void requestingACodeAlwaysSucceedsAndDelegates() {
        controller(true).requestSignupEmailCode(new com.agrimarket.api.dto.SignupEmailCodeRequest("me@farm.test"));
        verify(otp).requestSignupCode("me@farm.test");
    }
}
