package com.agrimarket.util;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * One place for the format rules on user-entered data (emails, phone numbers, names, bank details,
 * links). The web forms check the same rules (frontend/src/utils/validation.js); this side is the
 * authoritative one because the API can be called directly.
 *
 * <p>The {@code isValid*} checks treat {@code null}/blank as invalid; the bean-validation annotations
 * in {@link com.agrimarket.util.validation} let blanks through so they can be combined with
 * {@code @NotBlank} for required fields. Violations become a 400 {@code VALIDATION} response via
 * {@code GlobalExceptionHandler}.
 */
public final class InputValidator {

    private InputValidator() {
    }

    // ---------- Messages (kept in step with the web form messages) ----------

    public static final String EMAIL_MESSAGE = "must be a valid email address, e.g. name@example.com";
    public static final String PHONE_MESSAGE =
            "must be the country code plus 9 digits, e.g. +27 82 123 4567 or 082 123 4567";
    public static final String EMAIL_OR_PHONE_MESSAGE =
            "must be a valid email address or a phone number like +27 82 123 4567";
    public static final String PERSON_NAME_MESSAGE =
            "may only contain letters, spaces, apostrophes, dots and hyphens (up to 100 characters)";
    public static final String BUSINESS_NAME_MESSAGE =
            "must start with a letter or number and may only contain letters, numbers, spaces and & ' . , - ( ) / +";
    public static final String BANK_ACCOUNT_MESSAGE = "must be 6 to 16 digits";
    public static final String BRANCH_CODE_MESSAGE = "must be 6 digits";
    public static final String MEDIA_URLS_MESSAGE =
            "must be comma-separated image links (https://... or /api/...) of up to 500 characters each";

    // ---------- Email ----------

    /**
     * Practical email check: one @, no spaces, a dot in the domain, a 2+ letter ending, and the
     * usual length limits (64 before @, 254 overall).
     */
    private static final Pattern EMAIL = Pattern.compile(
            "^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]{1,64}@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?"
                    + "(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*\\.[A-Za-z]{2,24}$");

    public static boolean isValidEmail(String raw) {
        if (raw == null) return false;
        String e = raw.trim();
        return e.length() <= 254 && EMAIL.matcher(e).matches() && !e.contains("..");
    }

    // ---------- Phone: country code + 9 national digits ----------

    /** Supported dialling codes and their national number length (keep in sync with the web rules). */
    public static final Map<String, Integer> NATIONAL_LENGTH_BY_CODE = Map.of(
            "27", 9,   // South Africa
            "263", 9); // Zimbabwe

    public static final String DEFAULT_COUNTRY_CODE = "27";

    /**
     * Accepted shapes (spaces, dashes, dots and brackets ignored): {@code +27821234567},
     * {@code +27 82 123 4567}, {@code 0027821234567}, {@code 0821234567}, {@code 821234567}.
     * Local shapes are read as South African numbers.
     */
    public static boolean isValidPhone(String raw) {
        return normalizePhone(raw) != null;
    }

    /** E.164 form ({@code +27821234567}) or null when invalid. */
    public static String normalizePhone(String raw) {
        if (raw == null) return null;
        String s = raw.trim().replaceAll("[\\s\\-().]", "");
        if (s.isEmpty() || !s.matches("\\+?\\d+")) return null;

        if (s.startsWith("+") || s.startsWith("00")) {
            String digits = s.startsWith("+") ? s.substring(1) : s.substring(2);
            for (Map.Entry<String, Integer> c : NATIONAL_LENGTH_BY_CODE.entrySet()) {
                if (digits.startsWith(c.getKey())) {
                    String national = digits.substring(c.getKey().length());
                    if (national.startsWith("0")) national = national.substring(1); // +27 082...
                    return national.length() == c.getValue() ? "+" + c.getKey() + national : null;
                }
            }
            return null; // unsupported country
        }

        String national = s.startsWith("0") ? s.substring(1) : s;
        return national.length() == NATIONAL_LENGTH_BY_CODE.get(DEFAULT_COUNTRY_CODE)
                ? "+" + DEFAULT_COUNTRY_CODE + national
                : null;
    }

    /** Normalised phone when valid, else the trimmed input (null/blank stays null). */
    public static String normalizePhoneOrKeep(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String n = normalizePhone(raw);
        return n != null ? n : raw.trim();
    }

    public static boolean isValidEmailOrPhone(String raw) {
        return isValidEmail(raw) || isValidPhone(raw);
    }

    // ---------- Names ----------

    /** Letters (any language), spaces, apostrophes, hyphens and dots; 1–100 characters, starts with a letter. */
    private static final Pattern PERSON_NAME = Pattern.compile("^\\p{L}[\\p{L}\\p{M} .'’-]{0,99}$");

    public static boolean isValidPersonName(String raw) {
        if (raw == null) return false;
        return PERSON_NAME.matcher(raw.trim().replaceAll("\\s+", " ")).matches();
    }

    /**
     * Business / bank / display names: start with a letter or digit; letters, digits, spaces and
     * {@code & ' . , - ( ) / +}. Length is limited separately with {@code @Size}.
     */
    private static final Pattern BUSINESS_NAME = Pattern.compile("^[\\p{L}\\p{N}][\\p{L}\\p{M}\\p{N} &'’.,()/+-]*$");

    public static boolean isValidBusinessName(String raw) {
        if (raw == null) return false;
        return BUSINESS_NAME.matcher(raw.trim().replaceAll("\\s+", " ")).matches();
    }

    // ---------- Bank details ----------

    /** Digits only once spaces and dashes are removed, 6–16 long. */
    public static boolean isValidBankAccountNumber(String raw) {
        return raw != null && raw.replaceAll("[\\s-]", "").matches("\\d{6,16}");
    }

    /** South African universal / branch codes are 6 digits. */
    public static boolean isValidBranchCode(String raw) {
        return raw != null && raw.replaceAll("\\s", "").matches("\\d{6}");
    }

    /** Account number with spaces and dashes removed (null/blank stays null). */
    public static String normalizeDigits(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return raw.replaceAll("[\\s-]", "");
    }

    // ---------- Links ----------

    /**
     * Listing image references: comma-separated entries, each an absolute http(s) link or a
     * server path starting with "/", no whitespace, at most 500 characters. Blank entries ignored.
     */
    public static boolean isValidMediaUrls(String raw) {
        if (raw == null) return false;
        for (String part : raw.split(",")) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            if (p.length() > 500 || p.matches(".*\\s.*")) return false;
            String lower = p.toLowerCase(Locale.ROOT);
            boolean ok = (p.startsWith("/") && !p.startsWith("//"))
                    || lower.matches("^https?://[^/\\s]+(/.*)?$");
            if (!ok) return false;
        }
        return true;
    }
}
