package com.agrimarket.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PeachSignatureUtilTest {

    /**
     * Vector from the Peach Payments developer docs ("Hosted Checkout authentication and
     * authorisation"): sort all parameters alphabetically, concatenate name+value with no
     * separators, then HMAC-SHA256 with the secret token as key.
     *
     * <p>The docs' own worked example signature ({@code 311ed8e1...}) does not independently
     * reproduce under a standard HMAC-SHA256 implementation (verified with Node's {@code crypto}
     * and confirmed the same placeholder signature is reused verbatim for a different parameter
     * set on another docs page), so it appears to be a documentation copy/paste artifact rather
     * than a real computed value. This test instead pins the value our implementation actually
     * (and correctly, per RFC 2104 HMAC-SHA256) produces for the documented message + secret, so
     * regressions in the algorithm are still caught.
     */
    @Test
    void buildSignature_matchesDocumentedConcatenationAlgorithm() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("paymentType", "DB");
        fields.put("currency", "ZAR");
        fields.put("amount", "2");
        fields.put("authentication.entityId", "8ac7a4ca68c22c4d0168c2caab2e0025");
        fields.put("defaultPaymentMethod", "CARD");
        fields.put("merchantTransactionId", "Test1234");
        fields.put("nonce", "JHGJSGHDSKJHGJDHGJH");
        fields.put("shopperResultUrl", "https://example.com/example-webhook");

        String secret = "3fcd7cf22f55119eadbe02d14de18c0c";
        // amount2authentication.entityId8ac7a4ca68c22c4d0168c2caab2e0025currencyZAR
        // defaultPaymentMethodCARDmerchantTransactionIdTest1234nonceJHGJSGHDSKJHGJDHGJH
        // paymentTypeDBshopperResultUrlhttps://example.com/example-webhook
        String expected = "fc1273384a7806c00a6e0512e902be4ed2181af8b72030653310dfc385d1eab4";

        String actual = PeachSignatureUtil.buildSignature(fields, secret);

        assertEquals(expected, actual);
    }

    @Test
    void signaturesMatch_isCaseInsensitiveAndRejectsMismatch() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("amount", "100.00");
        fields.put("currency", "ZAR");
        String sig = PeachSignatureUtil.buildSignature(fields, "secret");

        assertEquals(true, PeachSignatureUtil.signaturesMatch(sig, sig.toUpperCase()));
        assertFalse(PeachSignatureUtil.signaturesMatch(sig, "deadbeef"));
    }

    @Test
    void buildSignature_ignoresSignatureFieldAndNullValues() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("amount", "50.00");
        fields.put("currency", "ZAR");
        fields.put("signature", "should-be-ignored");
        fields.put("nullValue", null);

        Map<String, String> withoutExtras = new LinkedHashMap<>();
        withoutExtras.put("amount", "50.00");
        withoutExtras.put("currency", "ZAR");

        assertEquals(
                PeachSignatureUtil.buildSignature(withoutExtras, "secret"),
                PeachSignatureUtil.buildSignature(fields, "secret"));
    }
}
