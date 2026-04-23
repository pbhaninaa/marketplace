package com.agrimarket.api;

import com.agrimarket.api.dto.ForgotPasswordRequest;
import com.agrimarket.api.dto.ProviderRegisterRequest;
import com.agrimarket.api.dto.ResetPasswordRequest;
import com.agrimarket.api.error.ApiException;
import com.agrimarket.service.BootstrapService;
import com.agrimarket.service.PasswordResetService;
import com.agrimarket.service.ProviderRegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public self-service under {@code /api/public/**} (permitted without JWT). Provider signup lives here so it is
 * not shadowed by authenticated {@code /api/provider/**} rules.
 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicRegistrationController {

    private final BootstrapService bootstrapService;
    private final ProviderRegistrationService providerRegistrationService;
    private final PasswordResetService passwordResetService;

    @PostMapping("/provider/register")
    @ResponseStatus(HttpStatus.CREATED)
    public void registerProvider(@Valid @RequestBody ProviderRegisterRequest req) {
        if (bootstrapService.needsFirstAdmin()) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "ADMIN_SETUP_REQUIRED",
                    "Create the platform administrator first at /setup before provider sign-up is enabled.");
        }
        providerRegistrationService.register(req);
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
