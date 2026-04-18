package com.agrimarket.service;

import com.agrimarket.api.dto.FirstAdminRequest;
import com.agrimarket.api.error.ApiException;
import com.agrimarket.domain.UserAccount;
import com.agrimarket.domain.UserRole;
import com.agrimarket.repo.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BootstrapService {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    public boolean needsFirstAdmin() {
        return !userAccountRepository.existsByRole(UserRole.PLATFORM_ADMIN);
    }

    @Transactional
    public void registerFirstAdmin(FirstAdminRequest req) {
        if (!needsFirstAdmin()) {
            throw new ApiException(HttpStatus.CONFLICT, "ADMIN_EXISTS", "A platform administrator is already registered.");
        }
        if (userAccountRepository.findByEmailIgnoreCase(req.email()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "Email already in use");
        }
        UserAccount admin = new UserAccount(req.email(), passwordEncoder.encode(req.password()), UserRole.PLATFORM_ADMIN, null);
        userAccountRepository.save(admin);
    }
}
