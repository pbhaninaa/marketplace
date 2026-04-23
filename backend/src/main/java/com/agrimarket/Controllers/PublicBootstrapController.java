package com.agrimarket.api;

import com.agrimarket.api.dto.FirstAdminRequest;
import com.agrimarket.api.dto.SetupStatusResponse;
import com.agrimarket.service.BootstrapService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicBootstrapController {

    private final BootstrapService bootstrapService;

    @GetMapping("/setup-status")
    public SetupStatusResponse setupStatus() {
        return new SetupStatusResponse(bootstrapService.needsFirstAdmin());
    }

    @PostMapping("/first-admin")
    public void firstAdmin(@Valid @RequestBody FirstAdminRequest req) {
        bootstrapService.registerFirstAdmin(req);
    }
}
