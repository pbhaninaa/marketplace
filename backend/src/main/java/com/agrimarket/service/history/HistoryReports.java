package com.agrimarket.service.history;

import com.agrimarket.service.history.HistoryService.OrderRow;
import com.agrimarket.service.history.HistoryService.PaymentRow;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Turns history rows into CSV / Excel downloads. */
public final class HistoryReports {

    private HistoryReports() {}

    public static ResponseEntity<byte[]> orders(List<OrderRow> rows, SpreadsheetExport.Format format, boolean includeProvider) {
        List<String> headers = new ArrayList<>(List.of("Order #", "Date (UTC)"));
        if (includeProvider) headers.add("Provider");
        headers.addAll(List.of(
                "Customer", "Email", "Phone", "Items", "Item count", "Fulfilment",
                "Status", "Payment status", "Payment method", "Delivery fee", "Total"));
        List<List<Object>> data = new ArrayList<>(rows.size());
        for (OrderRow r : rows) {
            List<Object> row = new ArrayList<>();
            row.add(r.id());
            row.add(r.createdAt());
            if (includeProvider) row.add(r.providerName());
            row.add(r.customerName());
            row.add(r.customerEmail());
            row.add(r.customerPhone());
            row.add(r.items());
            row.add(r.itemCount());
            row.add(r.fulfilment());
            row.add(r.status());
            row.add(r.paymentStatus());
            row.add(r.paymentMethod());
            row.add(r.deliveryFee());
            row.add(r.totalAmount());
            data.add(row);
        }
        return download(SpreadsheetExport.write(format, "Orders", headers, data), format, "orders");
    }

    public static ResponseEntity<byte[]> payments(
            List<PaymentRow> rows, SpreadsheetExport.Format format, boolean includeProvider) {
        List<String> headers = new ArrayList<>(List.of("Payment #", "Date (UTC)"));
        if (includeProvider) headers.add("Provider");
        headers.addAll(List.of("Type", "Order #", "Rental #", "Customer", "Email", "Method", "Status", "Amount"));
        List<List<Object>> data = new ArrayList<>(rows.size());
        for (PaymentRow r : rows) {
            List<Object> row = new ArrayList<>();
            row.add(r.id());
            row.add(r.createdAt());
            if (includeProvider) row.add(r.providerName());
            row.add(r.kind());
            row.add(r.orderId());
            row.add(r.rentalId());
            row.add(r.customerName());
            row.add(r.customerEmail());
            row.add(r.method());
            row.add(r.status());
            row.add(r.amount());
            data.add(row);
        }
        return download(SpreadsheetExport.write(format, "Payments", headers, data), format, "payments");
    }

    private static ResponseEntity<byte[]> download(byte[] body, SpreadsheetExport.Format format, String name) {
        String filename = name + "-" + LocalDate.now() + "." + format.extension;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(format.contentType));
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        return ResponseEntity.ok().headers(headers).body(body);
    }
}
