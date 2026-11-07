package com.agrimarket.api;

import com.agrimarket.api.dto.ProfileResponse;
import com.agrimarket.api.dto.ProfileUpdateRequest;
import com.agrimarket.security.MarketUserPrincipal;
import com.agrimarket.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in user's own account details (any role with a stored account). */
@RestController
@RequestMapping("/api/me/profile")
@RequiredArgsConstructor
public class MeProfileController {

    private final ProfileService profileService;

    @GetMapping
    public ProfileResponse get(@AuthenticationPrincipal MarketUserPrincipal principal) {
        return profileService.get(principal);
    }

    @PutMapping
    public ProfileResponse update(
            @AuthenticationPrincipal MarketUserPrincipal principal, @Valid @RequestBody ProfileUpdateRequest req) {
        return profileService.update(principal, req);
    }
}
