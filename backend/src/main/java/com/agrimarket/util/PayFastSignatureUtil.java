package com.agrimarket.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Map;

/**
 * PayFast MD5 signatures. Two different rules, as in PayFast's own documentation:
 *
 * <ul>
 *   <li><b>Checkout</b> ({@link #checkoutSignature}): the fields we send, in PayFast's documented
 *       field order, skipping blank values, each value trimmed and URL-encoded like PHP
 *       {@code urlencode} (space as {@code +}, upper-case hex).</li>
 *   <li><b>ITN</b> ({@link #itnParamString} / {@link #itnSignature}): every field PayFast posted, in
 *       the order received, except {@code signature}, <i>including empty ones</i> (a real ITN carries
 *       blank fields such as {@code custom_str3=} or {@code name_last=}, and PayFast signs them).
 *       Values are encoded as received, not trimmed.</li>
 * </ul>
 *
 * In both cases {@code &passphrase=...} is appended when a passphrase is set, and the MD5 hex is lower
 * case.
 */
public final class PayFastSignatureUtil {

    private PayFastSignatureUtil() {}

    /** Signature for the hosted checkout form. {@code fields} must be in PayFast's documented order. */
    public static String checkoutSignature(Map<String, String> fields, String passphrase) {
        StringBuilder sb = new StringBuilder();
        if (fields != null) {
            for (Map.Entry<String, String> entry : fields.entrySet()) {
                String key = entry.getKey();
                if (key == null || "signature".equals(key)) {
                    continue;
                }
                String value = entry.getValue();
                if (value == null || value.trim().isEmpty()) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append('&');
                }
                sb.append(key).append('=').append(urlEncode(value.trim()));
            }
        }
        return md5Hex(withPassphrase(sb.toString(), passphrase));
    }

    /**
     * The ITN parameter string: all posted fields in order except {@code signature}, blanks included.
     * This exact string (without passphrase) is also what is posted back to PayFast's validate endpoint.
     */
    public static String itnParamString(Map<String, String> postedInOrder) {
        StringBuilder sb = new StringBuilder();
        if (postedInOrder != null) {
            for (Map.Entry<String, String> entry : postedInOrder.entrySet()) {
                String key = entry.getKey();
                if (key == null || "signature".equals(key)) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append('&');
                }
                sb.append(key).append('=').append(urlEncode(entry.getValue() == null ? "" : entry.getValue()));
            }
        }
        return sb.toString();
    }

    /** Expected signature of an ITN. */
    public static String itnSignature(Map<String, String> postedInOrder, String passphrase) {
        return md5Hex(withPassphrase(itnParamString(postedInOrder), passphrase));
    }

    /** Constant-time, case-insensitive comparison of two hex signatures. */
    public static boolean signaturesMatch(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        byte[] expectedBytes = expected.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII);
        byte[] actualBytes = actual.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expectedBytes, actualBytes);
    }

    /** PHP {@code urlencode}: unreserved {@code A-Z a-z 0-9 - _ .}, space as {@code +}, upper-case hex. */
    public static String urlEncode(String value) {
        if (value == null) {
            return "";
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        StringBuilder out = new StringBuilder(bytes.length * 3);
        for (byte b : bytes) {
            int c = b & 0xFF;
            if ((c >= 'A' && c <= 'Z')
                    || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '-'
                    || c == '_'
                    || c == '.') {
                out.append((char) c);
            } else if (c == ' ') {
                out.append('+');
            } else {
                out.append('%').append(String.format("%02X", c));
            }
        }
        return out.toString();
    }

    private static String withPassphrase(String paramString, String passphrase) {
        if (passphrase == null || passphrase.trim().isEmpty()) {
            return paramString;
        }
        return paramString + "&passphrase=" + urlEncode(passphrase.trim());
    }

    static String md5Hex(String data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 not available", e);
        }
    }
}
