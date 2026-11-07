package com.agrimarket.security;

import com.agrimarket.api.error.ApiException;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;

/**
 * Rules every new password must pass (provider sign-up, first admin, support users, staff
 * accounts, password change and reset).
 *
 * <p>Follows NIST SP 800-63B: length plus a blocklist of common / breached passwords, rather than
 * forced character classes. Existing passwords are not re-checked; this only applies when a
 * password is set. The web client mirrors these rules in {@code frontend/src/utils/passwordPolicy.js}.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 64;
    /** BCrypt only uses the first 72 bytes and Spring Security 6.3+ rejects longer input. */
    private static final int MAX_BCRYPT_BYTES = 72;

    public static final String ERROR_CODE = "WEAK_PASSWORD";

    private static final String BLOCKLIST_RESOURCE = "/security/common-passwords.txt";
    private static final Set<String> COMMON = loadBlocklist();

    private PasswordPolicy() {
    }

    /**
     * @param email the account's email; the part before {@code @} must not appear in the password.
     * @return an error message for the user, or empty when the password is acceptable.
     */
    public static Optional<String> validate(String password, String email) {
        if (password == null || password.isBlank()) {
            return Optional.of("Password is required");
        }
        if (password.length() < MIN_LENGTH) {
            return Optional.of("Password must be at least " + MIN_LENGTH + " characters");
        }
        if (password.length() > MAX_LENGTH
                || password.getBytes(StandardCharsets.UTF_8).length > MAX_BCRYPT_BYTES) {
            return Optional.of("Password must be at most " + MAX_LENGTH + " characters");
        }
        String lower = password.toLowerCase(Locale.ROOT);
        if (isCommon(lower)) {
            return Optional.of("This password is too common. Choose something harder to guess");
        }
        if (password.chars().distinct().count() < 3) {
            return Optional.of("Password is too simple. Use a mix of different characters");
        }
        String name = emailName(email);
        if (name.length() >= 3 && lower.contains(name)) {
            return Optional.of("Password must not contain your email name");
        }
        return Optional.empty();
    }

    /** Throws a 400 {@link ApiException} with code {@value #ERROR_CODE} when the password is not acceptable. */
    public static void require(String password, String email) {
        validate(password, email).ifPresent(msg -> {
            throw new ApiException(HttpStatus.BAD_REQUEST, ERROR_CODE, msg);
        });
    }

    /** Lower-case part of the email before {@code @} (or the whole value when there is no {@code @}). */
    private static String emailName(String email) {
        if (email == null) return "";
        String e = email.trim().toLowerCase(Locale.ROOT);
        int at = e.indexOf('@');
        return at >= 0 ? e.substring(0, at) : e;
    }

    /** Common password, also with trailing digits / symbols stripped ("password123!" → "password"). */
    private static boolean isCommon(String lower) {
        if (COMMON.contains(lower)) return true;
        String stripped = lower.replaceAll("[^a-z]+$", "");
        return stripped.length() >= 4 && COMMON.contains(stripped);
    }

    private static Set<String> loadBlocklist() {
        Set<String> set = new HashSet<>();
        try (InputStream in = PasswordPolicy.class.getResourceAsStream(BLOCKLIST_RESOURCE)) {
            if (in == null) return set;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    String s = line.trim().toLowerCase(Locale.ROOT);
                    if (!s.isEmpty() && !s.startsWith("#")) set.add(s);
                }
            }
        } catch (Exception ignored) {
            // Blocklist is defence in depth; length rules still apply without it.
        }
        return set;
    }
}
