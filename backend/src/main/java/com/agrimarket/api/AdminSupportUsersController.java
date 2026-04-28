package com.agrimarket.api;

import com.agrimarket.api.dto.AdminSupportUserResponse;
import com.agrimarket.api.dto.CreateSupportUserRequest;
import com.agrimarket.service.AdminService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AdminSupportUsersController {

    private final AdminService adminService;

    @GetMapping("/api/admin/support-users")
    public List<AdminSupportUserResponse> list() {
        return adminService.listSupportUsers();
    }

    @PostMapping("/api/admin/create-support-user")
    public void create(@Valid @RequestBody CreateSupportUserRequest req) {
        adminService.createSupportUser(req);
    }
}

