package com.agrimarket.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PayFastSignatureUtilTest {

    @Test
    void urlEncodeMatchesPhpUrlencode() {
        assertThat(PayFastSignatureUtil.urlEncode("Hello World & Co*~")).isEqualTo("Hello+World+%26+Co%2A%7E");
        assertThat(PayFastSignatureUtil.urlEncode("https://shop.example/payment/return?ref=ab12"))
                .isEqualTo("https%3A%2F%2Fshop.example%2Fpayment%2Freturn%3Fref%3Dab12");
        assertThat(PayFastSignatureUtil.urlEncode("Café a-b_c.d")).isEqualTo("Caf%C3%A9+a-b_c.d");
    }

    @Test
    void checkoutSignatureUsesFieldOrderSkipsBlanksTrimsAndAppendsPassphrase() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("merchant_id", "10000100");
        fields.put("merchant_key", "46f0cd694581a");
        fields.put("return_url", "https://shop.example/payment/return?ref=abc");
        fields.put("cancel_url", "https://shop.example/payment/return?ref=abc&cancelled=1");
        fields.put("notify_url", "https://api.example/api/public/payfast/notify");
        fields.put("name_first", " Thandi ");
        fields.put("name_last", "   ");
        fields.put("email_address", "thandi@example.com");
        fields.put("m_payment_id", "abc");
        fields.put("amount", "150.00");
        fields.put("item_name", "Green Acres order");
        fields.put("custom_str1", "ORDER");
        fields.put("payment_method", "cc");
        fields.put("signature", "ignored");

        String expectedString = "merchant_id=10000100&merchant_key=46f0cd694581a"
                + "&return_url=https%3A%2F%2Fshop.example%2Fpayment%2Freturn%3Fref%3Dabc"
                + "&cancel_url=https%3A%2F%2Fshop.example%2Fpayment%2Freturn%3Fref%3Dabc%26cancelled%3D1"
                + "&notify_url=https%3A%2F%2Fapi.example%2Fapi%2Fpublic%2Fpayfast%2Fnotify"
                + "&name_first=Thandi&email_address=thandi%40example.com&m_payment_id=abc&amount=150.00"
                + "&item_name=Green+Acres+order&custom_str1=ORDER&payment_method=cc"
                + "&passphrase=jt7NOE43FZPn";

        assertThat(PayFastSignatureUtil.checkoutSignature(fields, "jt7NOE43FZPn")).isEqualTo(md5(expectedString));
        // Without a passphrase nothing is appended.
        assertThat(PayFastSignatureUtil.checkoutSignature(fields, ""))
                .isEqualTo(md5(expectedString.replace("&passphrase=jt7NOE43FZPn", "")));
    }

    /** A realistic ITN body: PayFast posts empty fields and signs them. */
    static Map<String, String> realisticItn() {
        Map<String, String> itn = new LinkedHashMap<>();
        itn.put("m_payment_id", "0f1e2d3c4b5a69788796a5b4c3d2e1f0");
        itn.put("pf_payment_id", "1089250");
        itn.put("payment_status", "COMPLETE");
        itn.put("item_name", "Green Acres order");
        itn.put("item_description", "");
        itn.put("amount_gross", "150.00");
        itn.put("amount_fee", "-3.45");
        itn.put("amount_net", "146.55");
        itn.put("custom_str1", "ORDER");
        itn.put("custom_str2", "");
        itn.put("custom_str3", "");
        itn.put("custom_str4", "");
        itn.put("custom_str5", "");
        itn.put("custom_int1", "");
        itn.put("custom_int2", "");
        itn.put("custom_int3", "");
        itn.put("custom_int4", "");
        itn.put("custom_int5", "");
        itn.put("name_first", "Thandi");
        itn.put("name_last", "");
        itn.put("email_address", "thandi@example.com");
        itn.put("merchant_id", "10000100");
        return itn;
    }

    @Test
    void itnSignatureIncludesBlankFieldsInReceivedOrder() {
        Map<String, String> itn = realisticItn();
        String paramString = PayFastSignatureUtil.itnParamString(itn);

        assertThat(paramString)
                .startsWith("m_payment_id=0f1e2d3c4b5a69788796a5b4c3d2e1f0&pf_payment_id=1089250&payment_status=COMPLETE")
                .contains("&item_description=&amount_gross=150.00&amount_fee=-3.45")
                .contains("&custom_str2=&custom_str3=&custom_str4=&custom_str5=&custom_int1=")
                .contains("&name_last=&email_address=thandi%40example.com")
                .endsWith("&merchant_id=10000100");

        String signature = md5(paramString + "&passphrase=jt7NOE43FZPn");
        assertThat(PayFastSignatureUtil.itnSignature(itn, "jt7NOE43FZPn")).isEqualTo(signature);

        // The posted signature itself is never part of the string, wherever it appears.
        Map<String, String> withSignature = new LinkedHashMap<>(itn);
        withSignature.put("signature", signature);
        assertThat(PayFastSignatureUtil.itnSignature(withSignature, "jt7NOE43FZPn")).isEqualTo(signature);

        // Skipping blank fields (the checkout rule) gives a different signature: it would reject this ITN.
        assertThat(PayFastSignatureUtil.checkoutSignature(itn, "jt7NOE43FZPn")).isNotEqualTo(signature);
    }

    @Test
    void signaturesMatchIsCaseInsensitiveAndRejectsNulls() {
        assertThat(PayFastSignatureUtil.signaturesMatch("abc123", "ABC123")).isTrue();
        assertThat(PayFastSignatureUtil.signaturesMatch("abc123", "abc124")).isFalse();
        assertThat(PayFastSignatureUtil.signaturesMatch(null, "abc")).isFalse();
        assertThat(PayFastSignatureUtil.signaturesMatch("abc", null)).isFalse();
    }

    private static String md5(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
