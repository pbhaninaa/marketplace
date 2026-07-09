package com.agrimarket.service;

import com.agrimarket.api.dto.AppNotificationResponse;
import com.agrimarket.api.error.ApiException;
import com.agrimarket.domain.AppNotification;
import com.agrimarket.domain.AppNotificationType;
import com.agrimarket.domain.Order;
import com.agrimarket.domain.Provider;
import com.agrimarket.domain.ProviderPermissionKey;
import com.agrimarket.domain.UserAccount;
import com.agrimarket.domain.UserRole;
import com.agrimarket.repo.AppNotificationRepository;
import com.agrimarket.repo.ProviderStaffPermissionRepository;
import com.agrimarket.repo.UserAccountRepository;
import com.agrimarket.security.MarketUserPrincipal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AppNotificationService {

    private final AppNotificationRepository appNotificationRepository;
    private final UserAccountRepository userAccountRepository;
    private final ProviderStaffPermissionRepository providerStaffPermissionRepository;

    @Transactional(readOnly = true)
    public List<AppNotificationResponse> listMine(MarketUserPrincipal actor) {
        return appNotificationRepository.findTop50ByUser_IdOrderByCreatedAtDesc(actor.getUserId()).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(MarketUserPrincipal actor) {
        return appNotificationRepository.countByUser_IdAndReadFalse(actor.getUserId());
    }

    @Transactional
    public void markRead(MarketUserPrincipal actor, Long id) {
        AppNotification n = appNotificationRepository
                .findByIdAndUser_Id(id, actor.getUserId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOTIFICATION", "Not found"));
        n.setRead(true);
        appNotificationRepository.save(n);
    }

    @Transactional
    public void markAllRead(MarketUserPrincipal actor) {
        List<AppNotification> rows =
                appNotificationRepository.findByUser_IdOrderByCreatedAtDesc(actor.getUserId());
        for (AppNotification n : rows) {
            if (!n.isRead()) {
                n.setRead(true);
            }
        }
        appNotificationRepository.saveAll(rows);
    }

    @Transactional
    public void notifyNewOrder(Order order) {
        if (order == null || order.getProvider() == null) {
            return;
        }
        Provider provider = order.getProvider();
        String title = "New order #" + order.getId();
        String body = "Guest " + (order.getGuestName() != null ? order.getGuestName() : "customer")
                + " placed an order totaling " + order.getTotalAmount() + ".";
        String link = "/provider/orders";
        for (UserAccount u : recipientsForNewOrder(provider)) {
            save(u, AppNotificationType.NEW_ORDER, title, body, link);
        }
    }

    @Transactional
    public void notifySubscriptionProofPending(Long providerId, String providerName) {
        String title = "Subscription proof pending";
        String body = "Provider " + (providerName != null ? providerName : ("#" + providerId))
                + " uploaded a subscription payment proof.";
        for (UserAccount admin : userAccountRepository.findByRoleOrderByEmailAsc(UserRole.PLATFORM_ADMIN)) {
            save(admin, AppNotificationType.SUBSCRIPTION_PROOF_PENDING, title, body, "/admin/manual-verifications");
        }
        for (UserAccount support : userAccountRepository.findByRoleOrderByEmailAsc(UserRole.SUPPORT)) {
            save(support, AppNotificationType.SUBSCRIPTION_PROOF_PENDING, title, body, "/support");
        }
    }

    @Transactional
    public void notifySubscriptionDecision(Provider provider, boolean approved, String note) {
        if (provider == null) {
            return;
        }
        String title = approved ? "Subscription approved" : "Subscription proof rejected";
        String body = approved
                ? "Your subscription payment proof was approved."
                : ("Your subscription payment proof was rejected"
                        + (note != null && !note.isBlank() ? ": " + note : "."));
        List<UserAccount> owners =
                userAccountRepository.findByProvider_IdAndRole(provider.getId(), UserRole.PROVIDER_OWNER);
        for (UserAccount owner : owners) {
            save(owner, AppNotificationType.SUBSCRIPTION_PROOF_DECISION, title, body, "/provider/subscription");
        }
    }

    @Transactional
    public void notifyOrderStatusByGuestEmail(Order order, String statusLabel) {
        if (order == null || order.getGuestEmail() == null || order.getGuestEmail().isBlank()) {
            return;
        }
        userAccountRepository.findByEmailIgnoreCase(order.getGuestEmail().trim()).ifPresent(client -> {
            if (client.getRole() == UserRole.CLIENT) {
                String title = "Order #" + order.getId() + " update";
                String body = "Your order is now " + statusLabel + ".";
                save(client, AppNotificationType.ORDER_STATUS, title, body, "/");
            }
        });
    }

    private List<UserAccount> recipientsForNewOrder(Provider provider) {
        List<UserAccount> out = new ArrayList<>();
        Set<ProviderPermissionKey> orderPerms =
                EnumSet.of(ProviderPermissionKey.ORDERS_READ, ProviderPermissionKey.ORDERS_WRITE);
        for (UserAccount u : userAccountRepository.findByProvider_IdOrderByEmailAsc(provider.getId())) {
            if (!u.isEnabled()) {
                continue;
            }
            if (u.getRole() == UserRole.PROVIDER_OWNER) {
                out.add(u);
                continue;
            }
            List<ProviderPermissionKey> keys =
                    providerStaffPermissionRepository.findKeys(provider.getId(), u.getId());
            boolean ok = keys.stream().anyMatch(orderPerms::contains);
            if (ok) {
                out.add(u);
            }
        }
        return out;
    }

    private void save(UserAccount user, AppNotificationType type, String title, String body, String link) {
        appNotificationRepository.save(new AppNotification(user, type, title, body, link));
    }

    private AppNotificationResponse toDto(AppNotification n) {
        return new AppNotificationResponse(
                n.getId(),
                n.getNotificationType(),
                n.getTitle(),
                n.getBody(),
                n.getLinkPath(),
                n.isRead(),
                n.getCreatedAt());
    }
}
