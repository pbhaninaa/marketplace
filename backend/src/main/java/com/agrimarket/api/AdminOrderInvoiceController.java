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
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
public class AdminOrderInvoiceController {

    private final OrderInvoiceService orderInvoiceService;

    @GetMapping("/invoice")
    public ResponseEntity<byte[]> downloadInvoice(@RequestParam("orderRef") String orderRef) {
        byte[] body = orderInvoiceService.buildPurchaseInvoicePdfForAdminOrSupport(orderRef);
        return asPdfAttachment(body, orderRef);
    }

    static ResponseEntity<byte[]> asPdfAttachment(byte[] body, String orderRef) {
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
