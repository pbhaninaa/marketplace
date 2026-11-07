package com.agrimarket.api;

import com.agrimarket.api.dto.FirstAdminRequest;
import com.agrimarket.api.dto.SetupStatusResponse;
import com.agrimarket.service.BootstrapService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
public class PublicSetupController {

    private final BootstrapService bootstrapService;
    private final boolean requireSignupEmailVerification;

    public PublicSetupController(
            BootstrapService bootstrapService,
            @Value("${app.signup.require-email-verification:true}") boolean requireSignupEmailVerification) {
        this.bootstrapService = bootstrapService;
        this.requireSignupEmailVerification = requireSignupEmailVerification;
    }

    @GetMapping("/setup-status")
    public SetupStatusResponse status() {
        return new SetupStatusResponse(bootstrapService.needsFirstAdmin(), requireSignupEmailVerification);
    }

    @PostMapping("/first-admin")
    @ResponseStatus(HttpStatus.OK)
    public void createFirstAdmin(@RequestBody FirstAdminRequest req) {
        bootstrapService.registerFirstAdmin(req);
    }
}
