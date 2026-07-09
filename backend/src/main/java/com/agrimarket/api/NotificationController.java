package com.agrimarket.api;

import com.agrimarket.api.dto.AppNotificationResponse;
import com.agrimarket.security.MarketUserPrincipal;
import com.agrimarket.service.AppNotificationService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final AppNotificationService appNotificationService;

    @GetMapping
    public List<AppNotificationResponse> list(@AuthenticationPrincipal MarketUserPrincipal actor) {
        return appNotificationService.listMine(actor);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal MarketUserPrincipal actor) {
        return Map.of("count", appNotificationService.unreadCount(actor));
    }

    @PostMapping("/{id}/read")
    public Map<String, String> markRead(
            @AuthenticationPrincipal MarketUserPrincipal actor, @PathVariable Long id) {
        appNotificationService.markRead(actor, id);
        return Map.of("status", "read");
    }

    @PostMapping("/read-all")
    public Map<String, String> markAllRead(@AuthenticationPrincipal MarketUserPrincipal actor) {
        appNotificationService.markAllRead(actor);
        return Map.of("status", "read");
    }
}
