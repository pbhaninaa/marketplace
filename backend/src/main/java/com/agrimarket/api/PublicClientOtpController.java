package com.agrimarket.api;

import com.agrimarket.api.dto.ClientOtpRequest;
import com.agrimarket.api.dto.ClientOtpVerifyRequest;
import com.agrimarket.api.dto.ClientOtpVerifyResponse;
import com.agrimarket.service.ClientOtpService;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/client/otp")
@RequiredArgsConstructor
public class PublicClientOtpController {

    private final ClientOtpService clientOtpService;

    @PostMapping("/request")
    public ResponseEntity<Map<String, String>> request(@Valid @RequestBody ClientOtpRequest req) {
        // SIT/local only: hand the code back so the verify page can fill it in (nothing is sent there).
        // UAT/PROD: never present, the code only goes out by email/SMS.
        return clientOtpService
                .requestOtp(req.target())
                .map(code -> ResponseEntity.ok(Map.of("testCode", code)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/verify")
    public ClientOtpVerifyResponse verify(@Valid @RequestBody ClientOtpVerifyRequest req) {
        return clientOtpService.verify(req.target(), req.code());
    }
}

