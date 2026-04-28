package com.agrimarket.service;

import com.agrimarket.api.dto.CreateSupportUserRequest;
import com.agrimarket.api.dto.AdminSupportUserResponse;
import com.agrimarket.api.dto.AdminUserResponse;
import com.agrimarket.api.dto.ListingResponse;
import com.agrimarket.api.dto.StaffMemberResponse;
import com.agrimarket.api.error.ApiException;
import com.agrimarket.domain.*;
import com.agrimarket.repo.PasswordResetTokenRepository;
import com.agrimarket.repo.ListingRepository;
import com.agrimarket.repo.CartLineRepository;
import com.agrimarket.repo.RentalBookingRepository;
import com.agrimarket.repo.ProviderStaffPermissionRepository;
import com.agrimarket.repo.ProviderRepository;
import com.agrimarket.repo.UserAccountRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserAccountRepository userAccountRepository;
    private final ProviderRepository providerRepository;
    private final ListingRepository listingRepository;
    private final CartLineRepository cartLineRepository;
    private final RentalBookingRepository rentalBookingRepository;
    private final ProviderStaffPermissionRepository providerStaffPermissionRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void createSupportUser(CreateSupportUserRequest req) {
        if (userAccountRepository.findByEmailIgnoreCase(req.email()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "Email already exists");
        }
        UserAccount u = new UserAccount(req.email(), passwordEncoder.encode(req.password()), UserRole.SUPPORT, null);
        userAccountRepository.save(u);
    }

    @Transactional
    public void updateProviderStatus(Long providerId, ProviderStatus status) {
        Provider p = providerRepository
                .findById(providerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROVIDER", "Provider not found"));
        p.setStatus(status);
    }

    @Transactional(readOnly = true)
    public List<AdminSupportUserResponse> listSupportUsers() {
        return userAccountRepository.findByRoleOrderByEmailAsc(UserRole.SUPPORT).stream()
                .map(u -> new AdminSupportUserResponse(u.getId(), u.getEmail(), u.isEnabled()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ListingResponse> listProviderListings(Long providerId) {
        Provider p = providerRepository
                .findById(providerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROVIDER", "Provider not found"));
        return listingRepository.findAllByProvider_Id(p.getId()).stream()
                .map(ListingMapper::toResponse)
                .toList();
    }

    @Transactional
    public void deleteProviderListing(Long providerId, Long listingId) {
        providerRepository
                .findById(providerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROVIDER", "Provider not found"));
        listingRepository.deleteByIdAndProvider_Id(listingId, providerId);
    }

    @Transactional(readOnly = true)
    public List<StaffMemberResponse> listProviderStaff(Long providerId) {
        Provider p = providerRepository
                .findById(providerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROVIDER", "Provider not found"));
        return userAccountRepository.findByProvider_IdOrderByEmailAsc(p.getId()).stream()
                .map(this::toStaffResponse)
                .toList();
    }

    @Transactional
    public void disableProviderStaff(Long providerId, Long staffUserId) {
        Provider p = providerRepository
                .findById(providerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROVIDER", "Provider not found"));
        UserAccount u = userAccountRepository
                .findByIdAndProvider_Id(staffUserId, p.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "STAFF", "User not found in this provider"));
        if (u.getRole() == UserRole.PROVIDER_OWNER) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OWNER_IMMUTABLE",
                    "Provider owner cannot be disabled here.");
        }
        u.setEnabled(false);
        userAccountRepository.save(u);
        providerStaffPermissionRepository.deleteByProvider_IdAndUser_Id(p.getId(), u.getId());
    }

    @Transactional(readOnly = true)
    public Page<ListingResponse> listAllListings(Pageable pageable) {
        return listingRepository.findAll(pageable).map(ListingMapper::toResponse);
    }

    public record OrderItemDTO(
            Long listingId,
            String listingName,
            int quantity,
            BigDecimal unitPrice
    ) {}

    @Transactional
    public void deleteListing(Long listingId) {
        var l = listingRepository
                .findById(listingId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LISTING", "Listing not found"));

        // If listing is already used in an order or booking, we must keep it for
        // history.
        if (cartLineRepository.existsByListing_Id(listingId) || rentalBookingRepository.existsByListing_Id(listingId)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "LISTING_IN_USE",
                    "Listing is already used in an order/booking. Unpublish it instead of deleting.");
        }

        // Safe to delete
        listingRepository.delete(l);
    }

    @Transactional
    public int deleteAllListingsSafe() {
        int deleted = 0;
        for (var l : listingRepository.findAll()) {
            Long id = l.getId();
            if (cartLineRepository.existsByListing_Id(id) || rentalBookingRepository.existsByListing_Id(id)) {
                l.setActive(false);
                listingRepository.save(l);
                continue;
            }
            listingRepository.delete(l);
            deleted++;
        }
        return deleted;
    }

    @Transactional
    public void setListingActive(Long listingId, boolean active) {
        var l = listingRepository
                .findById(listingId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LISTING", "Listing not found"));
        l.setActive(active);
        listingRepository.save(l);
    }

    @Transactional(readOnly = true)
    public Page<AdminUserResponse> listUsers(Pageable pageable) {
        return userAccountRepository.findAll(pageable).map(u -> new AdminUserResponse(
                u.getId(),
                u.getEmail(),
                u.getDisplayName(),
                u.getRole(),
                u.isEnabled(),
                u.getProvider() != null ? u.getProvider().getId() : null,
                u.getProvider() != null ? u.getProvider().getName() : null));
    }

    @Transactional
    public void deleteUser(Long userId) {
        UserAccount u = userAccountRepository
                .findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER", "User not found"));
        if (u.getRole() == UserRole.PLATFORM_ADMIN) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ADMIN_IMMUTABLE",
                    "Platform admin cannot be deleted via this endpoint.");
        }
        // Soft delete: disable + invalidate reset tokens. Keeps order history / audit
        // data intact.
        u.setEnabled(false);
        userAccountRepository.save(u);
        passwordResetTokenRepository.deleteByUser_Id(u.getId());
        if (u.getProvider() != null) {
            providerStaffPermissionRepository.deleteByProvider_IdAndUser_Id(u.getProvider().getId(), u.getId());
        }
    }

    @Transactional
    public int deleteAllUsersExcept(Long keepUserId) {
        int changed = 0;
        for (var u : userAccountRepository.findAll()) {
            if (u.getId().equals(keepUserId))
                continue;
            if (!u.isEnabled())
                continue;
            u.setEnabled(false);
            userAccountRepository.save(u);
            passwordResetTokenRepository.deleteByUser_Id(u.getId());
            if (u.getProvider() != null) {
                providerStaffPermissionRepository.deleteByProvider_IdAndUser_Id(u.getProvider().getId(), u.getId());
            }
            changed++;
        }
        return changed;
    }

    private StaffMemberResponse toStaffResponse(UserAccount u) {
        boolean owner = u.getRole() == UserRole.PROVIDER_OWNER;
        EnumSet<ProviderPermissionKey> perms = EnumSet.noneOf(ProviderPermissionKey.class);
        if (!owner && u.getProvider() != null) {
            List<ProviderPermissionKey> keys = providerStaffPermissionRepository.findKeys(u.getProvider().getId(),
                    u.getId());
            if (!keys.isEmpty()) {
                perms = EnumSet.copyOf(keys);
            }
        }
        return new StaffMemberResponse(
                u.getId(),
                u.getEmail(),
                u.getRole(),
                u.isEnabled(),
                owner,
                u.getStaffRateUnit(),
                u.getStaffCompensationRate(),
                perms);
    }
}
