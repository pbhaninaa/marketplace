package com.agrimarket.api;

import com.agrimarket.api.dto.ChangePasswordRequest;
import com.agrimarket.api.dto.LoginRequest;
import com.agrimarket.api.dto.LoginResponse;
import com.agrimarket.api.error.ApiException;
import com.agrimarket.security.MarketUserPrincipal;
import com.agrimarket.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req);
    }

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(
            @AuthenticationPrincipal MarketUserPrincipal user, @Valid @RequestBody ChangePasswordRequest req) {
        if (user == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH", "Unauthorized");
        }
        authService.changePassword(user, req);
    }
}
