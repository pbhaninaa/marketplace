package com.agrimarket.api;

import com.agrimarket.security.MarketUserPrincipal;
import com.agrimarket.service.AdminMaintenanceService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/maintenance")
@RequiredArgsConstructor
public class AdminMaintenanceController {

    private final AdminMaintenanceService adminMaintenanceService;

    @PostMapping("/clean-db")
    public Map<String, Object> cleanDb(@AuthenticationPrincipal MarketUserPrincipal actor) {
        // Keep the caller's account so the UI doesn't lock itself out during dev resets.
        return adminMaintenanceService.cleanDbKeepAdmin(actor.getUserId());
    }
}

