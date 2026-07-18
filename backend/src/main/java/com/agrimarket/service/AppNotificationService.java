package com.agrimarket.service;

import com.agrimarket.api.dto.AppNotificationResponse;
import com.agrimarket.api.error.ApiException;
import com.agrimarket.domain.AppNotification;
import com.agrimarket.domain.AppNotificationType;
import com.agrimarket.domain.CartLine;
import com.agrimarket.domain.Listing;
import com.agrimarket.domain.Order;
import com.agrimarket.domain.Provider;
import com.agrimarket.domain.ProviderPermissionKey;
import com.agrimarket.domain.RentalBooking;
import com.agrimarket.domain.SubscriptionPlan;
import com.agrimarket.domain.UserAccount;
import com.agrimarket.domain.UserRole;
import com.agrimarket.repo.AppNotificationRepository;
import com.agrimarket.repo.ProviderStaffPermissionRepository;
import com.agrimarket.repo.UserAccountRepository;
import com.agrimarket.security.MarketUserPrincipal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Unified notification fan-out (Wheel Hub pattern): in-app + email + SMS.
 * <ul>
 *   <li>In-app: always for account holders</li>
 *   <li>Email: clients always; providers when Premium (active PREMIUM plan)</li>
 *   <li>SMS: clients on order/rental lifecycle; provider owners on new order when phone set</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AppNotificationService {

    private static final Logger log = LoggerFactory.getLogger(AppNotificationService.class);

    private final AppNotificationRepository appNotificationRepository;
    private final UserAccountRepository userAccountRepository;
    private final ProviderStaffPermissionRepository providerStaffPermissionRepository;
    private final SubscriptionService subscriptionService;
    private final EmailService emailService;
    private final SmsService smsService;

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

    /** Checkout: purchase order(s) placed. */
    @Transactional
    public void notifyCheckout(
            List<Order> orders,
            List<RentalBooking> rentals,
            String guestName,
            String guestEmail,
            String guestPhone,
            String paymentMethod,
            String deliveryOrPickup) {
        Provider provider = null;
        if (orders != null && !orders.isEmpty()) {
            provider = orders.get(0).getProvider();
        } else if (rentals != null && !rentals.isEmpty()) {
            provider = rentals.get(0).getProvider();
        }
        if (provider == null) {
            return;
        }

        List<String> purchaseItems = formatPurchaseItems(orders);
        List<String> rentalItems = formatRentalItems(rentals);
        String paymentLabel = formatPaymentMethod(paymentMethod);
        List<String> emailLines = buildCheckoutEmailLines(
                purchaseItems, rentalItems, paymentLabel, deliveryOrPickup, orders, rentals);

        // Client confirmation (email + SMS always; in-app if CLIENT account)
        String clientTitle = "Order confirmation";
        String clientBody = summarizeCheckout(purchaseItems, rentalItems, paymentLabel);
        deliverClient(
                guestEmail,
                guestPhone,
                guestName,
                AppNotificationType.NEW_ORDER,
                clientTitle,
                clientBody,
                emailLines,
                "/",
                true,
                true);

        // Provider: in-app to order-permission staff; email/SMS to owners (Premium email)
        String providerTitle = "New order received";
        String providerBody = "Customer " + nullToEmpty(guestName) + " (" + nullToEmpty(guestPhone) + "). "
                + clientBody;
        List<UserAccount> staffRecipients = recipientsForOrders(provider);
        for (UserAccount u : staffRecipients) {
            saveInApp(u, AppNotificationType.NEW_ORDER, providerTitle, providerBody, "/provider/orders");
        }
        boolean premiumEmail = providerEmailAlertsEnabled(provider.getId());
        List<String> providerEmailLines = new ArrayList<>();
        providerEmailLines.add(
                "Customer: " + nullToEmpty(guestName) + " (" + nullToEmpty(guestPhone) + ")");
        providerEmailLines.addAll(emailLines);
        for (UserAccount owner : ownersOf(provider)) {
            if (premiumEmail) {
                sendEmail(
                        owner.getEmail(),
                        providerTitle,
                        "A customer placed an order on your store.",
                        "New order",
                        providerEmailLines);
            }
            if (owner.getPhoneNumber() != null && !owner.getPhoneNumber().isBlank()) {
                smsService.sendSms(owner.getPhoneNumber(), providerTitle + ": " + providerBody);
            }
        }

        if (rentals != null) {
            for (RentalBooking b : rentals) {
                String rentalLabel = formatRentalItem(b);
                for (UserAccount u : recipientsForRentals(provider)) {
                    saveInApp(
                            u,
                            AppNotificationType.NEW_RENTAL,
                            "New rental #" + b.getId(),
                            "Guest " + nullToEmpty(guestName) + " booked: " + rentalLabel + ".",
                            "/provider/orders");
                }
            }
        }
    }

    @Transactional
    public void notifyNewOrder(Order order) {
        if (order == null || order.getProvider() == null) {
            return;
        }
        Provider provider = order.getProvider();
        String title = "New order #" + order.getId();
        String body = "Guest " + nullToEmpty(order.getGuestName()) + " placed an order totaling "
                + order.getTotalAmount() + ".";
        for (UserAccount u : recipientsForOrders(provider)) {
            saveInApp(u, AppNotificationType.NEW_ORDER, title, body, "/provider/orders");
        }
        boolean premiumEmail = providerEmailAlertsEnabled(provider.getId());
        for (UserAccount owner : ownersOf(provider)) {
            if (premiumEmail) {
                sendEmail(
                        owner.getEmail(),
                        title,
                        body,
                        "A customer placed an order on your store.",
                        List.of(
                                "Order ID: " + order.getId(),
                                "Total: " + order.getTotalAmount(),
                                "Customer: " + nullToEmpty(order.getGuestName()) + " ("
                                        + nullToEmpty(order.getGuestPhone()) + ")"));
            }
            if (owner.getPhoneNumber() != null && !owner.getPhoneNumber().isBlank()) {
                smsService.sendSms(owner.getPhoneNumber(), title + " — " + body);
            }
        }
    }

    @Transactional
    public void notifyOrderStatus(Order order) {
        if (order == null) {
            return;
        }
        String status = order.getStatus() == null ? "" : order.getStatus().name();
        String title = "Order #" + order.getId() + " update";
        String body = "Your order is now " + status + ".";

        deliverClient(
                order.getGuestEmail(),
                order.getGuestPhone(),
                order.getGuestName(),
                AppNotificationType.ORDER_STATUS,
                title,
                body,
                null,
                "/",
                true,
                true);

        if (order.getProvider() != null) {
            String pTitle = "Purchase order status changed: " + status;
            String pBody = "Order #" + order.getId() + " is now " + status + ".";
            for (UserAccount u : recipientsForOrders(order.getProvider())) {
                saveInApp(u, AppNotificationType.ORDER_STATUS, pTitle, pBody, "/provider/orders");
            }
            if (providerEmailAlertsEnabled(order.getProvider().getId())) {
                for (UserAccount owner : ownersOf(order.getProvider())) {
                    sendEmail(
                            owner.getEmail(),
                            pTitle,
                            pBody,
                            "An order status was updated in your store.",
                            List.of(
                                    "Order ID: " + order.getId(),
                                    "New status: " + status,
                                    "Customer: " + nullToEmpty(order.getGuestName()) + " ("
                                            + nullToEmpty(order.getGuestPhone()) + ")"));
                }
            }
        }
    }

    @Transactional
    public void notifyOrderCancelled(Order order) {
        if (order == null) {
            return;
        }
        String title = "Order #" + order.getId() + " cancelled";
        String body = "Your order has been cancelled.";
        deliverClient(
                order.getGuestEmail(),
                order.getGuestPhone(),
                order.getGuestName(),
                AppNotificationType.ORDER_CANCELLED,
                title,
                body,
                null,
                "/",
                true,
                true);
        if (order.getProvider() != null) {
            String pTitle = "Order cancelled #" + order.getId();
            String pBody = "Order for " + nullToEmpty(order.getGuestName()) + " was cancelled.";
            for (UserAccount u : recipientsForOrders(order.getProvider())) {
                saveInApp(u, AppNotificationType.ORDER_CANCELLED, pTitle, pBody, "/provider/orders");
            }
            if (providerEmailAlertsEnabled(order.getProvider().getId())) {
                for (UserAccount owner : ownersOf(order.getProvider())) {
                    sendEmail(owner.getEmail(), pTitle, pBody, pBody, List.of("Order ID: " + order.getId()));
                }
            }
        }
    }

    @Transactional
    public void notifyRentalStatus(RentalBooking rental) {
        if (rental == null) {
            return;
        }
        String status = rental.getStatus() == null ? "" : rental.getStatus().name();
        String title = "Booking #" + rental.getId() + " update";
        String body = "Your rental booking is now " + status + ".";
        deliverClient(
                rental.getGuestEmail(),
                rental.getGuestPhone(),
                rental.getGuestName(),
                AppNotificationType.RENTAL_STATUS,
                title,
                body,
                null,
                "/",
                true,
                true);
        if (rental.getProvider() != null) {
            String pTitle = "Rental booking status changed: " + status;
            String pBody = "Booking #" + rental.getId() + " is now " + status + ".";
            for (UserAccount u : recipientsForRentals(rental.getProvider())) {
                saveInApp(u, AppNotificationType.RENTAL_STATUS, pTitle, pBody, "/provider/orders");
            }
            if (providerEmailAlertsEnabled(rental.getProvider().getId())) {
                for (UserAccount owner : ownersOf(rental.getProvider())) {
                    sendEmail(
                            owner.getEmail(),
                            pTitle,
                            pBody,
                            "A booking status was updated in your store.",
                            List.of(
                                    "Booking ID: " + rental.getId(),
                                    "New status: " + status,
                                    "Customer: " + nullToEmpty(rental.getGuestName()) + " ("
                                            + nullToEmpty(rental.getGuestPhone()) + ")"));
                }
            }
        }
    }

    @Transactional
    public void notifySubscriptionProofPending(Long providerId, String providerName) {
        String title = "Subscription proof pending";
        String body = "Provider " + (providerName != null ? providerName : ("#" + providerId))
                + " uploaded a subscription payment proof.";
        for (UserAccount admin : userAccountRepository.findByRoleOrderByEmailAsc(UserRole.PLATFORM_ADMIN)) {
            saveInApp(admin, AppNotificationType.SUBSCRIPTION_PROOF_PENDING, title, body, "/admin");
            sendEmail(
                    EmailPurpose.BILLING,
                    admin.getEmail(),
                    title,
                    body,
                    body,
                    List.of("Provider: " + providerName, "ID: " + providerId));
        }
        for (UserAccount support : userAccountRepository.findByRoleOrderByEmailAsc(UserRole.SUPPORT)) {
            saveInApp(support, AppNotificationType.SUBSCRIPTION_PROOF_PENDING, title, body, "/support");
            sendEmail(
                    EmailPurpose.BILLING,
                    support.getEmail(),
                    title,
                    body,
                    body,
                    List.of("Provider: " + providerName, "ID: " + providerId));
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
        for (UserAccount owner : ownersOf(provider)) {
            saveInApp(owner, AppNotificationType.SUBSCRIPTION_PROOF_DECISION, title, body, "/provider/subscription");
            // Subscription decisions always email (like Wheel Hub EFT-pending exception — critical path)
            sendEmail(
                    EmailPurpose.BILLING,
                    owner.getEmail(),
                    title,
                    body,
                    body,
                    List.of("Provider: " + provider.getName()));
            if (owner.getPhoneNumber() != null && !owner.getPhoneNumber().isBlank()) {
                smsService.sendSms(owner.getPhoneNumber(), title + " — " + body);
            }
        }
    }

    /** @deprecated use {@link #notifyOrderStatus(Order)} */
    @Transactional
    public void notifyOrderStatusByGuestEmail(Order order, String statusLabel) {
        notifyOrderStatus(order);
    }

    private void deliverClient(
            String email,
            String phone,
            String name,
            AppNotificationType type,
            String title,
            String body,
            List<String> emailLines,
            String link,
            boolean emailOn,
            boolean smsOn) {
        if (email != null && !email.isBlank()) {
            userAccountRepository.findByEmailIgnoreCase(email.trim()).ifPresent(client -> {
                if (client.getRole() == UserRole.CLIENT) {
                    saveInApp(client, type, title, body, link);
                }
            });
            if (emailOn) {
                if (emailLines != null && !emailLines.isEmpty()) {
                    sendEmail(
                            email,
                            title,
                            "Thanks for your order. Details are listed below.",
                            "Hello " + nullToEmpty(name),
                            emailLines);
                } else {
                    sendEmail(
                            email,
                            title,
                            body,
                            "Hello " + nullToEmpty(name),
                            List.of(body));
                }
            }
        }
        if (smsOn && phone != null && !phone.isBlank()) {
            smsService.sendSms(phone, title + " — " + body);
        }
    }

    private static final DateTimeFormatter RENTAL_DATE =
            DateTimeFormatter.ofPattern("dd MMM yyyy").withZone(ZoneId.of("Africa/Johannesburg"));

    private static List<String> formatPurchaseItems(List<Order> orders) {
        List<String> items = new ArrayList<>();
        if (orders == null) {
            return items;
        }
        for (Order order : orders) {
            if (order.getLines() == null) {
                continue;
            }
            for (CartLine line : order.getLines()) {
                items.add(formatPurchaseItem(line));
            }
        }
        return items;
    }

    private static String formatPurchaseItem(CartLine line) {
        Listing listing = line.getListing();
        String title = listing != null && listing.getTitle() != null && !listing.getTitle().isBlank()
                ? listing.getTitle().trim()
                : "Item";
        String price = listing != null && listing.getUnitPrice() != null
                ? " @ " + OrderInvoiceService.formatMoney(listing.getUnitPrice())
                : "";
        return line.getQuantity() + " × " + title + price;
    }

    private static List<String> formatRentalItems(List<RentalBooking> rentals) {
        List<String> items = new ArrayList<>();
        if (rentals == null) {
            return items;
        }
        for (RentalBooking booking : rentals) {
            items.add(formatRentalItem(booking));
        }
        return items;
    }

    private static String formatRentalItem(RentalBooking booking) {
        Listing listing = booking.getListing();
        String title = listing != null && listing.getTitle() != null && !listing.getTitle().isBlank()
                ? listing.getTitle().trim()
                : "Rental";
        StringBuilder sb = new StringBuilder(title);
        if (booking.getStartAt() != null && booking.getEndAt() != null) {
            sb.append(" (")
                    .append(RENTAL_DATE.format(booking.getStartAt()))
                    .append(" – ")
                    .append(RENTAL_DATE.format(booking.getEndAt()))
                    .append(")");
        }
        if (booking.getTotalAmount() != null) {
            sb.append(" — ").append(OrderInvoiceService.formatMoney(booking.getTotalAmount()));
        }
        return sb.toString();
    }

    private static String summarizeCheckout(
            List<String> purchaseItems, List<String> rentalItems, String paymentLabel) {
        String purchases = purchaseItems.isEmpty() ? "none" : String.join("; ", purchaseItems);
        String rentals = rentalItems.isEmpty() ? "none" : String.join("; ", rentalItems);
        return "Purchases: " + purchases + ". Rentals: " + rentals + ". Payment: " + paymentLabel + ".";
    }

    private static List<String> buildCheckoutEmailLines(
            List<String> purchaseItems,
            List<String> rentalItems,
            String paymentLabel,
            String deliveryOrPickup,
            List<Order> orders,
            List<RentalBooking> rentals) {
        List<String> lines = new ArrayList<>();
        lines.add("Payment: " + paymentLabel);
        if (deliveryOrPickup != null && !deliveryOrPickup.isBlank()) {
            lines.add("Fulfilment: " + truncate(deliveryOrPickup.trim(), 240));
        }
        if (purchaseItems.isEmpty()) {
            lines.add("Purchases: none");
        } else {
            lines.add("Purchases:");
            for (String item : purchaseItems) {
                lines.add("• " + item);
            }
            if (orders != null) {
                for (Order order : orders) {
                    if (order.getId() != null && order.getTotalAmount() != null) {
                        lines.add(
                                "Order #" + order.getId() + " total: "
                                        + OrderInvoiceService.formatMoney(order.getTotalAmount()));
                    }
                    if (order.getVerificationCode() != null && !order.getVerificationCode().isBlank()) {
                        lines.add("Order #" + order.getId() + " code: " + order.getVerificationCode());
                    }
                }
            }
        }
        if (rentalItems.isEmpty()) {
            lines.add("Rentals: none");
        } else {
            lines.add("Rentals:");
            for (String item : rentalItems) {
                lines.add("• " + item);
            }
            if (rentals != null) {
                for (RentalBooking booking : rentals) {
                    if (booking.getVerificationCode() != null && !booking.getVerificationCode().isBlank()) {
                        lines.add(
                                "Booking #" + booking.getId() + " code: " + booking.getVerificationCode());
                    }
                }
            }
        }
        return lines;
    }

    private static String formatPaymentMethod(String paymentMethod) {
        if (paymentMethod == null || paymentMethod.isBlank()) {
            return "—";
        }
        String normalized = paymentMethod.trim().replace('_', ' ');
        if (normalized.equalsIgnoreCase("CASH")) {
            return "Cash";
        }
        if (normalized.equalsIgnoreCase("EFT") || normalized.equalsIgnoreCase("MANUAL EFT")) {
            return "Manual EFT";
        }
        if (normalized.equalsIgnoreCase("CARD") || normalized.equalsIgnoreCase("PEACH")) {
            return "Card";
        }
        if (normalized.length() == 1) {
            return normalized.toUpperCase(Locale.ROOT);
        }
        return normalized.substring(0, 1).toUpperCase(Locale.ROOT) + normalized.substring(1).toLowerCase(Locale.ROOT);
    }

    private boolean providerEmailAlertsEnabled(Long providerId) {
        return subscriptionService
                .currentActive(providerId)
                .map(s -> s.getPlan() == SubscriptionPlan.PREMIUM)
                .orElse(false);
    }

    private List<UserAccount> ownersOf(Provider provider) {
        return userAccountRepository.findByProvider_IdAndRole(provider.getId(), UserRole.PROVIDER_OWNER);
    }

    private List<UserAccount> recipientsForOrders(Provider provider) {
        return recipientsWithPerms(
                provider,
                EnumSet.of(ProviderPermissionKey.ORDERS_READ, ProviderPermissionKey.ORDERS_WRITE));
    }

    private List<UserAccount> recipientsForRentals(Provider provider) {
        Set<ProviderPermissionKey> keys = EnumSet.of(
                ProviderPermissionKey.RENTALS_READ,
                ProviderPermissionKey.RENTALS_WRITE,
                ProviderPermissionKey.ORDERS_READ,
                ProviderPermissionKey.ORDERS_WRITE);
        return recipientsWithPerms(provider, keys);
    }

    private List<UserAccount> recipientsWithPerms(Provider provider, Set<ProviderPermissionKey> needed) {
        LinkedHashSet<UserAccount> out = new LinkedHashSet<>();
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
            if (keys.stream().anyMatch(needed::contains)) {
                out.add(u);
            }
        }
        return new ArrayList<>(out);
    }

    private void saveInApp(UserAccount user, AppNotificationType type, String title, String body, String link) {
        if (user == null) {
            return;
        }
        appNotificationRepository.save(new AppNotification(user, type, truncate(title, 200), truncate(body, 1000), link));
    }

    private void sendEmail(String to, String subject, String lead, String intro, List<String> lines) {
        sendEmail(EmailPurpose.INFO, to, subject, lead, intro, lines);
    }

    private void sendEmail(
            EmailPurpose purpose,
            String to,
            String subject,
            String lead,
            String intro,
            List<String> lines) {
        if (to == null || to.isBlank()) {
            return;
        }
        try {
            String plain = EmailTemplates.simpleText(subject, lines, null);
            String html = EmailTemplates.layout(subject, intro, lead, lines, null);
            emailService.send(purpose, to, subject, plain, html);
        } catch (Exception e) {
            log.debug("Email failed to {}: {}", to, e.getMessage());
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
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
