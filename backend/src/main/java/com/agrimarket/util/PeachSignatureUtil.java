package com.agrimarket.util;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Peach Payments signature generation and validation: HMAC-SHA256 of the sorted key+value
 * concatenation of the request/response fields, keyed with the merchant secret token.
 */
public final class PeachSignatureUtil {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private PeachSignatureUtil() {}

    public static String buildSignature(Map<String, String> fields, String secret) {
        TreeMap<String, String> sorted = new TreeMap<>();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            String key = entry.getKey();
            if (key == null || "signature".equalsIgnoreCase(key)) {
                continue;
            }
            String value = entry.getValue();
            if (value == null) {
                continue;
            }
            sorted.put(key, value);
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            sb.append(entry.getKey()).append(entry.getValue());
        }
        return hmacSha256Hex(sb.toString(), secret);
    }

    public static boolean signaturesMatch(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        byte[] expectedBytes = expected.toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.US_ASCII);
        byte[] actualBytes = actual.trim().toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expectedBytes, actualBytes);
    }

    private static String hmacSha256Hex(String data, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] digest = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HmacSHA256 not available", e);
        }
    }
}
