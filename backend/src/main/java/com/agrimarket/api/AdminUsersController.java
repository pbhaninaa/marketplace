package com.agrimarket.api;

import com.agrimarket.api.dto.AdminUserResponse;
import com.agrimarket.security.MarketUserPrincipal;
import com.agrimarket.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUsersController {

    private final AdminService adminService;

    @GetMapping
    public Page<AdminUserResponse> list(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "5") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return adminService.listUsers(pageable);
    }

    @DeleteMapping("/{id}")
    public void remove(@PathVariable Long id) {
        adminService.deleteUser(id);
    }

    @DeleteMapping
    public int removeAll(@AuthenticationPrincipal MarketUserPrincipal actor) {
        return adminService.deleteAllUsersExcept(actor.getUserId());
    }
}

