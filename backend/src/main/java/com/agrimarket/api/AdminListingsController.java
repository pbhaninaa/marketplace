package com.agrimarket.api;

import com.agrimarket.api.dto.ListingResponse;
import com.agrimarket.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/listings")
@RequiredArgsConstructor
public class AdminListingsController {

    private final AdminService adminService;

    public record SetActiveRequest(boolean active) {}

    @GetMapping
    public Page<ListingResponse> list(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "5") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return adminService.listAllListings(pageable);
    }

    @PatchMapping("/{id}/active")
    public void setActive(@PathVariable Long id, @Valid @RequestBody SetActiveRequest req) {
        adminService.setListingActive(id, req.active());
    }

    @DeleteMapping("/{id}")
    public void remove(@PathVariable Long id) {
        adminService.deleteListing(id);
    }

    @DeleteMapping
    public int removeAll() {
        return adminService.deleteAllListingsSafe();
    }
}

