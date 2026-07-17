package com.agrimarket.api;

import com.agrimarket.api.dto.PeachStatusResponse;
import com.agrimarket.service.PeachPaymentService;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public Peach Payments endpoints: guest return-page status polling and the Hosted Checkout
 * webhook / shopperResultUrl callback. Public by design — {@code /api/public/**} is permitAll
 * (see SecurityConfig) — status lookups are keyed by the unguessable merchantTransactionId only.
 */
@RestController
@RequestMapping("/api/public/peach")
@RequiredArgsConstructor
public class PeachController {

    private final PeachPaymentService peachPaymentService;

    @GetMapping("/configured")
    public Map<String, Boolean> configured() {
        return Map.of("configured", peachPaymentService.isConfigured());
    }

    @GetMapping("/status")
    public PeachStatusResponse status(@RequestParam("ref") String ref) {
        return peachPaymentService.getStatusByRef(ref);
    }

    /**
     * Peach webhook — must be publicly reachable. Also used as the target for
     * {@code shopperResultUrl}, since Checkout POSTs the same parameter shape to both.
     */
    @PostMapping(value = "/webhook", consumes = "application/x-www-form-urlencoded")
    public Map<String, String> webhookForm(HttpServletRequest request) {
        Map<String, String> params = callbackParameters(request, null);
        peachPaymentService.handleWebhook(params);
        return Map.of("status", "OK");
    }

    @PostMapping(value = "/webhook", consumes = "application/json")
    public Map<String, String> webhookJson(@RequestBody Map<String, Object> jsonBody) {
        Map<String, String> params = callbackParameters(null, jsonBody);
        peachPaymentService.handleWebhook(params);
        return Map.of("status", "OK");
    }

    /**
     * Peach returns the shopper with a POST. Process the same signed payload as a webhook, then use
     * 303 See Other so the browser lands on the SPA's GET route and can poll the committed status.
     */
    @PostMapping(value = "/return", consumes = "application/x-www-form-urlencoded")
    public ResponseEntity<Void> shopperReturnForm(HttpServletRequest request) {
        return processShopperReturn(callbackParameters(request, null));
    }

    @PostMapping(value = "/return", consumes = "application/json")
    public ResponseEntity<Void> shopperReturnJson(@RequestBody Map<String, Object> jsonBody) {
        return processShopperReturn(callbackParameters(null, jsonBody));
    }

    private ResponseEntity<Void> processShopperReturn(Map<String, String> params) {
        peachPaymentService.handleWebhook(params);
        String destination = peachPaymentService.getFrontendReturnUrl(params.get("merchantTransactionId"));
        return ResponseEntity.status(HttpStatus.SEE_OTHER).location(URI.create(destination)).build();
    }

    private static Map<String, String> callbackParameters(
            HttpServletRequest request, Map<String, Object> jsonBody) {
        Map<String, String> params = new LinkedHashMap<>();
        if (request != null) {
            request.getParameterMap().forEach((key, values) -> {
                if (values != null && values.length > 0 && values[0] != null) {
                    params.put(key, values[0]);
                }
            });
        }
        if (jsonBody != null) {
            flattenJson("", jsonBody, params);
        }
        return params;
    }

    @SuppressWarnings("unchecked")
    private static void flattenJson(String prefix, Map<String, Object> source, Map<String, String> target) {
        source.forEach((key, value) -> {
            if (value == null) {
                return;
            }
            String flattenedKey = prefix.isEmpty() ? key : prefix + "." + key;
            if (value instanceof Map<?, ?> nested) {
                flattenJson(flattenedKey, (Map<String, Object>) nested, target);
            } else {
                target.put(flattenedKey, String.valueOf(value));
            }
        });
    }
}
