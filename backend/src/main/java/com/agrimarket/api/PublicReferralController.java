package com.agrimarket.api;

import com.agrimarket.api.dto.ReferralDtos.CodeLookupResponse;
import com.agrimarket.service.ReferralService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Sign-up page helper: confirms a referral code and shows the referring business name only. */
@RestController
@RequestMapping("/api/public/referrals")
@RequiredArgsConstructor
public class PublicReferralController {

    private final ReferralService referralService;

    @GetMapping("/{code}")
    public CodeLookupResponse lookup(@PathVariable String code) {
        return referralService.lookup(code);
    }
}
