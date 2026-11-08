package com.agrimarket.api;

import com.agrimarket.api.dto.PeachStatusResponse;
import com.agrimarket.service.OnlinePaymentGatewayResolver;
import com.agrimarket.service.OnlinePaymentService;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Gateway-independent public payment endpoints (no secrets). */
@RestController
@RequestMapping("/api/public/payments")
@RequiredArgsConstructor
public class PublicPaymentsController {

    private final OnlinePaymentGatewayResolver gatewayResolver;
    private final OnlinePaymentService onlinePaymentService;

    /** Whether the online "Card / Instant EFT" option is available, and which gateway serves it. */
    @GetMapping("/online-status")
    public Map<String, Object> onlineStatus() {
        var gateway = gatewayResolver.activeGateway();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("available", gateway.isPresent());
        out.put("gateway", gateway.map(Enum::name).orElse(null));
        return out;
    }

    /** Return-page status for any gateway, keyed by the unguessable payment reference. */
    @GetMapping("/status")
    public PeachStatusResponse status(@RequestParam("ref") String ref) {
        return onlinePaymentService.getStatusByRef(ref);
    }
}
