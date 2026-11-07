package com.agrimarket.service;

import com.agrimarket.api.error.ApiException;
import com.agrimarket.domain.CartLine;
import com.agrimarket.domain.Order;
import com.agrimarket.domain.Provider;
import com.agrimarket.domain.RentalBooking;
import com.agrimarket.repo.OrderRepository;
import com.agrimarket.repo.RentalBookingRepository;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderInvoiceService {

    private static final DateTimeFormatter DT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter ORDER_NO_YEAR =
            DateTimeFormatter.ofPattern("yyyy").withZone(ZoneId.systemDefault());

    private final OrderRepository orderRepository;
    private final RentalBookingRepository rentalBookingRepository;

    @Transactional(readOnly = true)
    public byte[] buildPurchaseInvoicePdf(Long providerId, Long orderId) {
        Order o = orderRepository
                .findWithLinesById(orderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER", "Order not found"));
        if (!o.getProvider().getId().equals(providerId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ORDER", "Order not found");
        }
        return buildPurchaseInvoiceBytesPdf(o);
    }

    @Transactional(readOnly = true)
    public byte[] buildPurchaseInvoicePdfForAdminOrSupport(String orderRef) {
        Order o = resolvePurchaseOrderWithLines(orderRef);
        return buildPurchaseInvoiceBytesPdf(o);
    }

    /**
     * Guest invoice download. An order number (numeric id or friendly {@code ORD-YYYY-NNNNNN}) is sequential and
     * guessable, so it must be paired with the checkout email. The random verification code alone is accepted
     * (it is the guest's secret); if an email is also given it must match. Every mismatch is reported as the same
     * 404 so callers cannot tell which order numbers exist.
     */
    @Transactional(readOnly = true)
    public byte[] buildPurchaseInvoicePdfForGuest(String orderRef, String email) {
        String ref = orderRef == null ? "" : orderRef.trim();
        if (ref.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ORDER", "Order reference is required");
        }
        boolean emailGiven = email != null && !email.isBlank();
        Long orderNumber = parseOrderNumber(ref);
        Order o;
        if (orderNumber != null) {
            if (!emailGiven) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST, "ORDER", "Email is required when using numeric order number");
            }
            o = orderRepository.findWithLinesById(orderNumber).orElse(null);
        } else {
            o = findPurchaseOrderByVerificationRef(ref).orElse(null);
        }
        if (o == null || (emailGiven && !emailsEqual(email, o.getGuestEmail()))) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ORDER", "Invoice not available");
        }
        return buildPurchaseInvoiceBytesPdf(o);
    }

    /**
     * Parses an order number: plain digits or the friendly form {@code ORD-2026-000012}. Returns null when the
     * reference is not an order number (it is then treated as a verification code).
     */
    static Long parseOrderNumber(String rawRef) {
        String ref = rawRef == null ? "" : rawRef.trim();
        if (ref.toUpperCase(Locale.ROOT).startsWith("ORD-")) {
            ref = ref.substring(4).replaceAll("^[0-9]{4}-", "").replace("-", "");
        } else if (ref.isEmpty() || !ref.chars().allMatch(Character::isDigit)) {
            return null;
        }
        if (ref.isEmpty() || !ref.chars().allMatch(Character::isDigit)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ORDER", "Invalid order number");
        }
        try {
            return Long.parseLong(ref);
        } catch (NumberFormatException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ORDER", "Invalid order number");
        }
    }

    private static boolean emailsEqual(String a, String b) {
        if (a == null || b == null) return false;
        return a.trim().equalsIgnoreCase(b.trim());
    }

    /**
     * Resolves {@code orderRef} as numeric order id, or as the order verification code (with or without hyphen).
     * Admin/support only: performs no ownership check.
     */
    public Order resolvePurchaseOrderWithLines(String orderRef) {
        if (orderRef == null || orderRef.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ORDER", "Order reference is required");
        }
        String ref = orderRef.trim();
        Long orderNumber = parseOrderNumber(ref);
        if (orderNumber != null) {
            return orderRepository
                    .findWithLinesById(orderNumber)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER", "Order not found"));
        }
        return findPurchaseOrderByVerificationRef(ref)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER", "Order not found"));
    }

    private Optional<Order> findPurchaseOrderByVerificationRef(String ref) {
        Optional<Order> direct = orderRepository.findWithLinesByVerificationCode(ref.trim());
        if (direct.isPresent()) {
            return direct;
        }
        String compact = ref.replace("-", "").trim().toUpperCase(Locale.ROOT);
        if (compact.length() == 8) {
            String formatted = compact.substring(0, 4) + "-" + compact.substring(4);
            return orderRepository.findWithLinesByVerificationCode(formatted);
        }
        return Optional.empty();
    }

    private byte[] buildPurchaseInvoiceBytesPdf(Order o) {
        Provider provider = o.getProvider();
        String invoiceNo = formatInvoiceNumber(o.getId(), o.getCreatedAt());
        String orderNo = formatOrderNumber(o.getId(), o.getCreatedAt());
        StringBuilder lines = new StringBuilder();
        BigDecimal itemsSubtotal = BigDecimal.ZERO;
        for (CartLine line : o.getLines()) {
            BigDecimal unit = line.getListing().getUnitPrice();
            int qty = line.getQuantity();
            BigDecimal lineTotal = unit.multiply(BigDecimal.valueOf(qty));
            itemsSubtotal = itemsSubtotal.add(lineTotal);
            lines.append("<tr><td>")
                    .append(esc(line.getListing().getTitle()))
                    .append("</td><td class=\"num\">")
                    .append(qty)
                    .append("</td><td class=\"num\">R ")
                    .append(money(unit))
                    .append("</td><td class=\"num\">R ")
                    .append(money(lineTotal))
                    .append("</td></tr>");
        }

        BigDecimal deliveryFee = o.getDeliveryFee() == null ? BigDecimal.ZERO : o.getDeliveryFee();
        BigDecimal grandTotal = o.getTotalAmount() == null ? BigDecimal.ZERO : o.getTotalAmount();
        String html = htmlShell(
                "Invoice " + invoiceNo,
                invoiceNo,
                """
                <div class="row row--top">
                  <div class="col">
                    <div class="label">Bill To:</div>
                    <div class="who">%s</div>
                    <div class="muted">%s</div>
                    <div class="muted">%s</div>
                  </div>
                  <div class="col col--right">
                    <div class="label">From:</div>
                    <div class="who">%s</div>
                    <div class="muted">%s</div>
                  </div>
                </div>

                <div class="row row--meta">
                  <div class="col">
                    <div class="muted">Date: <strong class="strong">%s</strong></div>
                  </div>
                  <div class="col col--right">
                    <div class="muted">Order: <strong class="strong">%s</strong> · Code: <strong class="strong">%s</strong></div>
                  </div>
                </div>

                <table class="items">
                  <thead><tr><th>Description</th><th class="num">Qty</th><th class="num">Price</th><th class="num">Total</th></tr></thead>
                  <tbody>
                """
                        .formatted(
                                esc(o.getGuestName()),
                                esc(o.getGuestPhone()),
                                esc(o.getGuestEmail()),
                                esc(o.getProvider().getName()),
                                esc(o.getProvider().getLocation()),
                                esc(DT.format(o.getCreatedAt())),
                                esc(orderNo),
                                esc(o.getVerificationCode())
                        )
                        + lines +
                        """
                          </tbody>
                        </table>

                        <div class="totals-bar">
                          <table role="presentation" class="totals">
                            <tr>
                              <td class="tlabel">Sub Total</td>
                              <td class="tval">R %s</td>
                            </tr>
                            <tr>
                              <td class="tlabel">Delivery Fee</td>
                              <td class="tval">R %s</td>
                            </tr>
                            <tr class="grand">
                              <td class="tlabel">Total</td>
                              <td class="tval">R %s</td>
                            </tr>
                          </table>
                        </div>

                        <div class="footer">
                          <div class="note">
                            <div class="label">Note:</div>
                            <div class="line"></div>
                            <div class="line"></div>
                          </div>
                          <div class="thanks">Thank You!</div>
                        </div>
                        """
                                .formatted(
                                        esc(money(itemsSubtotal)),
                                        esc(money(deliveryFee)),
                                        esc(money(grandTotal))
                                )
                ,
                provider);

        return htmlToPdfBytes(html);
    }


    private static final Locale ZA_LOCALE = new Locale("en", "ZA");

    public static String formatMoney(BigDecimal amount) {
        if (amount == null) return "R 0.00";

        NumberFormat formatter = NumberFormat.getCurrencyInstance(ZA_LOCALE);
        return formatter.format(amount);
    }
    @Transactional(readOnly = true)
    public byte[] buildRentalInvoicePdf(Long providerId, Long bookingId) {
        RentalBooking b = rentalBookingRepository
                .findWithDetailsById(bookingId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BOOKING", "Booking not found"));
        if (!b.getProvider().getId().equals(providerId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "BOOKING", "Booking not found");
        }
        Provider provider = b.getProvider();
        String invoiceNo = formatInvoiceNumber(b.getId(), b.getCreatedAt());
        String orderNo = "RENT-" + (b.getId() == null ? "000000" : String.format(Locale.ROOT, "%06d", b.getId()));
        String listingTitle = b.getListing() != null ? b.getListing().getTitle() : "Rental";
        BigDecimal deliveryFee = b.getDeliveryFee() == null ? BigDecimal.ZERO : b.getDeliveryFee();
        BigDecimal grandTotal = b.getTotalAmount() == null ? BigDecimal.ZERO : b.getTotalAmount();
        BigDecimal subTotal = grandTotal.subtract(deliveryFee);
        if (subTotal.compareTo(BigDecimal.ZERO) < 0) {
            subTotal = BigDecimal.ZERO;
        }

        String body =
                """
                <div class="row row--top">
                  <div class="col">
                    <div class="label">Bill To:</div>
                    <div class="who">%s</div>
                    <div class="muted">%s</div>
                    <div class="muted">%s</div>
                  </div>
                  <div class="col col--right">
                    <div class="label">From:</div>
                    <div class="who">%s</div>
                    <div class="muted">%s</div>
                  </div>
                </div>

                <div class="row row--meta">
                  <div class="col">
                    <div class="muted">Date: <strong class="strong">%s</strong></div>
                  </div>
                  <div class="col col--right">
                    <div class="muted">Booking: <strong class="strong">%s</strong> · Status: <strong class="strong">%s</strong></div>
                  </div>
                </div>

                <table class="items">
                  <thead><tr><th>Description</th><th class="num">Qty</th><th class="num">Price</th><th class="num">Total</th></tr></thead>
                  <tbody>
                    <tr>
                      <td>%s<br/><span class="muted small">Window: %s → %s</span></td>
                      <td class="num">1</td>
                      <td class="num">R %s</td>
                      <td class="num">R %s</td>
                    </tr>
                  </tbody>
                </table>

                <div class="totals-bar">
                  <table role="presentation" class="totals">
                    <tr>
                      <td class="tlabel">Sub Total</td>
                      <td class="tval">R %s</td>
                    </tr>
                    <tr>
                      <td class="tlabel">Delivery Fee</td>
                      <td class="tval">R %s</td>
                    </tr>
                    <tr class="grand">
                      <td class="tlabel">Total</td>
                      <td class="tval">R %s</td>
                    </tr>
                  </table>
                </div>

                <div class="footer">
                  <div class="note">
                    <div class="label">Note:</div>
                    <div class="line"></div>
                    <div class="line"></div>
                  </div>
                  <div class="thanks">Thank You!</div>
                </div>
                """
                        .formatted(
                                esc(b.getGuestName()),
                                esc(b.getGuestPhone()),
                                esc(b.getGuestEmail()),
                                esc(b.getProvider().getName()),
                                esc(b.getProvider().getLocation()),
                                esc(DT.format(b.getCreatedAt())),
                                esc(orderNo),
                                esc(String.valueOf(b.getStatus())),
                                esc(listingTitle),
                                esc(DT.format(b.getStartAt())),
                                esc(DT.format(b.getEndAt())),
                                esc(money(subTotal)),
                                esc(money(subTotal)),
                                esc(money(subTotal)),
                                esc(money(deliveryFee)),
                                esc(money(grandTotal))
                        );
        String html = htmlShell("Invoice " + invoiceNo, invoiceNo, body, provider);
        return htmlToPdfBytes(html);
    }

    private static byte[] htmlToPdfBytes(String html) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "INVOICE", "Failed to generate PDF invoice");
        }
    }

    private static String formatOrderNumber(Long id, Instant createdAt) {
        long n = id == null ? 0 : id;
        String year = createdAt == null ? "0000" : ORDER_NO_YEAR.format(createdAt);
        return "ORD-" + year + "-" + String.format(Locale.ROOT, "%06d", n);
    }

    private static String formatInvoiceNumber(Long id, Instant createdAt) {
        long n = id == null ? 0 : id;
        String year = createdAt == null ? "0000" : ORDER_NO_YEAR.format(createdAt);
        // Keep the "-1" suffix to mimic common invoice numbering styles.
        return "INV-" + year + "-" + String.format(Locale.ROOT, "%05d", n) + "-1";
    }

    private static String htmlShell(String title, String invoiceNo, String bodyInner, Provider provider) {
        String fromName = provider != null && notBlank(provider.getName()) ? provider.getName() : "Provider";
        // System palette (fixed values; openhtmltopdf does not reliably support CSS variables).
        String canopy = "#1A3C34";
        String sage = "#3D7A66";
        String border = "rgba(0,0,0,.12)";
        String muted = "#5B6570";

        return "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\"/><title>"
                + esc(title)
                + "</title><style>"
                + "body{font-family:Arial, Helvetica, sans-serif;margin:0;color:#111;font-size:12px;}"
                + ".page{padding:26px 28px 22px;}"
                + ".hdr{background:" + canopy + ";color:#fff;padding:22px 28px 0;}"
                + ".hdr__row{width:100%;border-collapse:collapse;}"
                + ".hdr__row td{vertical-align:top;}"
                + ".hdr__title{font-size:26px;font-weight:800;letter-spacing:.08em;}"
                + ".hdr__no{text-align:right;font-size:12px;font-weight:700;letter-spacing:.04em;margin-top:6px;}"
                + ".hdr__no span{opacity:.92;}"
                + ".hdr__brand{font-size:11px;opacity:.88;margin-top:4px;}"
                + ".wave{height:44px;line-height:0;}"
                + ".label{font-weight:800;color:" + canopy + ";margin-bottom:3px;}"
                + ".who{font-weight:800;font-size:14px;color:#111;}"
                + ".strong{color:#111;font-weight:800;}"
                + ".muted{color:" + muted + ";}"
                + ".small{font-size:11px;}"
                + ".row{width:100%;display:table;table-layout:fixed;margin:14px 0 0;}"
                + ".row--top{margin-top:0;}"
                + ".row--meta{margin-top:8px;}"
                + ".col{display:table-cell;vertical-align:top;}"
                + ".col--right{text-align:right;}"
                + "table.items{width:100%;border-collapse:collapse;margin-top:14px;}"
                + "table.items th,table.items td{border:1px solid " + border + ";padding:8px 10px;text-align:left;}"
                + "table.items th{background:" + sage + ";color:#fff;font-weight:800;text-transform:uppercase;font-size:11px;letter-spacing:.06em;}"
                + ".num{text-align:right;}"
                + ".totals-bar{margin-top:14px;display:table;width:100%;}"
                + ".totals{margin-left:auto;width:290px;border-collapse:collapse;}"
                + ".totals td{padding:7px 10px;border:1px solid " + border + ";}"
                + ".totals .tlabel{background:" + sage + ";color:#fff;font-weight:700;text-align:left;}"
                + ".totals .tval{text-align:right;font-weight:800;}"
                + ".totals tr.grand td{background:" + canopy + ";color:#fff;}"
                + ".footer{width:100%;display:table;margin-top:16px;}"
                + ".note{display:table-cell;vertical-align:top;width:58%;}"
                + ".note .line{height:12px;border-bottom:1px solid " + border + ";margin-top:10px;width:88%;}"
                + ".thanks{display:table-cell;vertical-align:bottom;text-align:right;font-size:28px;font-weight:300;color:" + canopy + ";letter-spacing:.01em;}"
                + "@media print{a{color:inherit;text-decoration:none;}}"
                + "</style></head><body>"
                + "<div class=\"hdr\">"
                + "<table class=\"hdr__row\" role=\"presentation\"><tr><td>"
                + "<div class=\"hdr__title\">INVOICE</div>"
                + "<div class=\"hdr__brand\">" + esc(fromName) + "</div>"
                + "</td><td>"
                + "<div class=\"hdr__no\">NO: <span>" + esc(invoiceNo) + "</span></div>"
                + "</td></tr></table>"
                + "</div>"
                + "<div class=\"wave\">"
                + "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"100%\" height=\"44\" viewBox=\"0 0 1000 80\" preserveAspectRatio=\"none\">"
                + "<path d=\"M0,30 C180,80 380,0 560,34 C720,62 840,64 1000,20 L1000,80 L0,80 Z\" fill=\"#ffffff\"/>"
                + "</svg>"
                + "</div>"
                + "<div class=\"page\">"
                + bodyInner
                + "</div>"
                + "</body></html>";
    }

    private static boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String money(BigDecimal v) {
        if (v == null) return "0.00";
        return String.format(Locale.US, "%.2f", v);
    }
}
