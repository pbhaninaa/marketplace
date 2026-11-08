package com.agrimarket.api;

import com.agrimarket.api.dto.ForgotPasswordRequest;
import com.agrimarket.api.dto.ProviderRegisterRequest;
import com.agrimarket.api.dto.ResetPasswordRequest;
import com.agrimarket.api.dto.SignupEmailCodeRequest;
import com.agrimarket.api.error.ApiException;
import com.agrimarket.service.BootstrapService;
import com.agrimarket.service.ClientOtpService;
import com.agrimarket.service.PasswordResetService;
import com.agrimarket.service.ProviderRegistrationService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
public class PublicRegistrationController {

    static final String EMAIL_CODE_INVALID_MESSAGE =
            "Enter the verification code we emailed you. Codes expire after a few minutes; request a new one if needed.";

    private final BootstrapService bootstrapService;
    private final ProviderRegistrationService providerRegistrationService;
    private final PasswordResetService passwordResetService;
    private final ClientOtpService clientOtpService;
    private final boolean requireEmailVerification;

    public PublicRegistrationController(
            BootstrapService bootstrapService,
            ProviderRegistrationService providerRegistrationService,
            PasswordResetService passwordResetService,
            ClientOtpService clientOtpService,
            @Value("${app.signup.require-email-verification:true}") boolean requireEmailVerification) {
        this.bootstrapService = bootstrapService;
        this.providerRegistrationService = providerRegistrationService;
        this.passwordResetService = passwordResetService;
        this.clientOtpService = clientOtpService;
        this.requireEmailVerification = requireEmailVerification;
    }

    /**
     * Step 1 of sign-up: email a one-time code to the owner address. Always 204, whether or not the
     * address already has an account, so this cannot be used to discover registered emails.
     */
    @PostMapping("/provider/register/email-code")
    public ResponseEntity<Map<String, String>> requestSignupEmailCode(@Valid @RequestBody SignupEmailCodeRequest req) {
        // SIT/local only: hand the code back so the form can fill it in (no real email is sent there).
        // UAT/PROD: never present, the code only goes out by email.
        return clientOtpService
                .requestSignupCode(req.email())
                .map(code -> ResponseEntity.ok(Map.of("testCode", code)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * Step 2: create the provider. The emailed code is checked BEFORE any "email already registered"
     * check, so only the owner of the inbox can learn whether it has an account. The code is used up
     * only once the account is saved, so fixing another field and resubmitting still works.
     */
    @PostMapping("/provider/register")
    @ResponseStatus(HttpStatus.CREATED)
    public void registerProvider(@Valid @RequestBody ProviderRegisterRequest req) {
        if (bootstrapService.needsFirstAdmin()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_SETUP_REQUIRED", "Platform admin must be created first");
        }
        if (requireEmailVerification
                && !clientOtpService.verifySignupCode(req.ownerEmail(), req.emailVerificationCode())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMAIL_CODE_INVALID", EMAIL_CODE_INVALID_MESSAGE);
        }
        providerRegistrationService.register(req);
        if (requireEmailVerification) {
            clientOtpService.consumeSignupCode(req.ownerEmail());
        }
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        passwordResetService.requestReset(req.email());
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        passwordResetService.resetPassword(req.token(), req.newPassword());
    }
}
