package com.agrimarket.api;

import com.agrimarket.api.error.ApiException;
import com.agrimarket.service.PayFastPaymentService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PayFast ITN (Instant Transaction Notification) endpoint. Public by design ({@code /api/public/**}
 * is permitAll); every ITN is verified by signature, merchant id, amount and PayFast's validate
 * endpoint before anything changes.
 */
@RestController
@RequestMapping("/api/public/payfast")
@RequiredArgsConstructor
public class PayFastController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PayFastController.class);

    private final PayFastPaymentService payFastPaymentService;

    /**
     * Answers 200 with an empty body once the ITN is handled or rejected (a rejected ITN is logged; a
     * retry would not change the outcome). Answers 503 when PayFast itself could not be reached for
     * validation, so PayFast retries later.
     */
    @PostMapping("/notify")
    public ResponseEntity<Void> notify(HttpServletRequest request) {
        // The servlet container keeps form fields in the order they were posted, which the ITN
        // signature depends on. Empty fields are kept: PayFast signs them too.
        Map<String, String> params = new LinkedHashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (key != null && values != null && values.length > 0) {
                params.put(key, values[0] == null ? "" : values[0]);
            }
        });
        try {
            payFastPaymentService.handleItn(params);
        } catch (ApiException e) {
            if (e.getStatus().is5xxServerError()) {
                log.warn("PayFast ITN for {} not processed yet: {}", params.get("m_payment_id"), e.getMessage());
                return ResponseEntity.status(e.getStatus()).build();
            }
            log.warn("PayFast ITN for {} rejected: {}", params.get("m_payment_id"), e.getMessage());
        }
        return ResponseEntity.ok().build();
    }
}
