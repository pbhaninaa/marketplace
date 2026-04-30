package com.agrimarket.api;

import com.agrimarket.service.OrderInvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicOrderInvoiceController {

    private final OrderInvoiceService orderInvoiceService;

    /**
     * Guest invoice: use numeric order id plus the checkout email, or the verification code (with or without hyphen).
     */
    @GetMapping("/order-invoice")
    public ResponseEntity<byte[]> downloadGuestInvoice(
            @RequestParam("orderRef") String orderRef, @RequestParam(value = "email", required = false) String email) {
        byte[] body = orderInvoiceService.buildPurchaseInvoicePdfForGuest(orderRef, email);
        return purchaseInvoiceResponse(body, orderRef);
    }

    private static ResponseEntity<byte[]> purchaseInvoiceResponse(byte[] body, String orderRef) {
        String safe = orderRef.replaceAll("[^a-zA-Z0-9_-]", "");
        if (safe.isEmpty()) {
            safe = "order";
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(
                ContentDisposition.attachment()
                        .filename("invoice-" + safe + ".pdf")
                        .build());
        return ResponseEntity.ok().headers(headers).body(body);
    }
}
