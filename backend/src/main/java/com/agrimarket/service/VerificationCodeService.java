package com.agrimarket.service;

import org.springframework.stereotype.Service;
import java.security.SecureRandom;

@Service
public class VerificationCodeService {

    private static final String CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // Excludes ambiguous chars
    private static final int CODE_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Generates a unique 8-character alphanumeric verification code.
     * Format: XXXX-XXXX (e.g., A3B7-K9M2)
     * Excludes ambiguous characters (0, O, 1, I) for clarity.
     */
    public String generateVerificationCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH + 1); // +1 for hyphen

        for (int i = 0; i < CODE_LENGTH; i++) {
            if (i == 4) {
                code.append('-'); // Add hyphen in the middle for readability
            }
            int index = RANDOM.nextInt(CHARACTERS.length());
            code.append(CHARACTERS.charAt(index));
        }

        return code.toString();
    }
}
