package com.agrimarket.api;

import com.agrimarket.api.dto.ClientOtpRequest;
import com.agrimarket.api.dto.ClientOtpVerifyRequest;
import com.agrimarket.api.dto.ClientOtpVerifyResponse;
import com.agrimarket.service.ClientOtpService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/client/otp")
@RequiredArgsConstructor
public class ClientOtpController {

    private final ClientOtpService clientOtpService;

    @PostMapping("/request")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void request(@Valid @RequestBody ClientOtpRequest req) {
        clientOtpService.requestOtp(req.target());
    }

    @PostMapping("/verify")
    public ClientOtpVerifyResponse verify(@Valid @RequestBody ClientOtpVerifyRequest req) {
        return clientOtpService.verify(req.target(), req.code());
    }
}

