package com.agrimarket.api;

import com.agrimarket.service.OrderInvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/support/orders")
@RequiredArgsConstructor
public class SupportOrderInvoiceController {

    private final OrderInvoiceService orderInvoiceService;

    @GetMapping("/invoice")
    public ResponseEntity<byte[]> downloadInvoice(@RequestParam("orderRef") String orderRef) {
        byte[] body = orderInvoiceService.buildPurchaseInvoicePdfForAdminOrSupport(orderRef);
        return AdminOrderInvoiceController.asPdfAttachment(body, orderRef);
    }
}
