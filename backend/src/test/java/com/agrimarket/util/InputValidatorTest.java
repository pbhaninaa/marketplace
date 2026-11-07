package com.agrimarket.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class InputValidatorTest {

    @Test
    void emails() {
        assertTrue(InputValidator.isValidEmail("name@example.com"));
        assertTrue(InputValidator.isValidEmail("guest+abc@checkout.test"));
        assertTrue(InputValidator.isValidEmail("  first.last@sub.example.co.za "));
        assertFalse(InputValidator.isValidEmail("a@b"));
        assertFalse(InputValidator.isValidEmail("no-at-sign.com"));
        assertFalse(InputValidator.isValidEmail("two@@example.com"));
        assertFalse(InputValidator.isValidEmail("dots..here@example.com"));
        assertFalse(InputValidator.isValidEmail("space in@example.com"));
        assertFalse(InputValidator.isValidEmail(""));
        assertFalse(InputValidator.isValidEmail(null));
    }

    @Test
    void phonesAcceptCountryCodePlusNineDigitsAndNormalise() {
        assertEquals("+27821234567", InputValidator.normalizePhone("+27 82 123 4567"));
        assertEquals("+27821234567", InputValidator.normalizePhone("+27821234567"));
        assertEquals("+27821234567", InputValidator.normalizePhone("0027821234567"));
        assertEquals("+27821234567", InputValidator.normalizePhone("082 123 4567"));
        assertEquals("+27821234567", InputValidator.normalizePhone("0821234567"));
        assertEquals("+27821234567", InputValidator.normalizePhone("821234567"));
        assertEquals("+27821234567", InputValidator.normalizePhone("+27 (0)82-123-4567"));
        assertEquals("+263771234567", InputValidator.normalizePhone("+263 77 123 4567"));
    }

    @Test
    void phonesRejectWrongLengthsLettersAndUnknownCountries() {
        assertNull(InputValidator.normalizePhone("082123456"));      // 8 national digits
        assertNull(InputValidator.normalizePhone("08212345678"));    // 10 national digits
        assertNull(InputValidator.normalizePhone("+2782123456"));
        assertNull(InputValidator.normalizePhone("+44 7911 123456")); // unsupported country
        assertNull(InputValidator.normalizePhone("082 ABC 4567"));
        assertNull(InputValidator.normalizePhone(""));
        assertNull(InputValidator.normalizePhone(null));
        assertFalse(InputValidator.isValidPhone("12345"));
    }

    @Test
    void normalizePhoneOrKeep() {
        assertEquals("+27821234567", InputValidator.normalizePhoneOrKeep(" 082 123 4567 "));
        assertEquals("abc", InputValidator.normalizePhoneOrKeep(" abc "));
        assertNull(InputValidator.normalizePhoneOrKeep("  "));
    }

    @Test
    void emailOrPhone() {
        assertTrue(InputValidator.isValidEmailOrPhone("client@example.test"));
        assertTrue(InputValidator.isValidEmailOrPhone("0821234567"));
        assertFalse(InputValidator.isValidEmailOrPhone("nope"));
    }

    @Test
    void personNames() {
        assertTrue(InputValidator.isValidPersonName("Thandi"));
        assertTrue(InputValidator.isValidPersonName("Jean-Luc O'Neil"));
        assertTrue(InputValidator.isValidPersonName("Zoë  van der Merwe"));
        assertTrue(InputValidator.isValidPersonName("J. Doe"));
        assertFalse(InputValidator.isValidPersonName("R2D2"));
        assertFalse(InputValidator.isValidPersonName("<script>"));
        assertFalse(InputValidator.isValidPersonName("-Leading"));
        assertFalse(InputValidator.isValidPersonName("a".repeat(101)));
        assertFalse(InputValidator.isValidPersonName(null));
    }

    @Test
    void businessNames() {
        assertTrue(InputValidator.isValidBusinessName("Test Farm Co"));
        assertTrue(InputValidator.isValidBusinessName("Smith & Sons (Pty) Ltd."));
        assertTrue(InputValidator.isValidBusinessName("4x4 Agri/Feeds"));
        assertTrue(InputValidator.isValidBusinessName("FNB"));
        assertFalse(InputValidator.isValidBusinessName("<b>Farm</b>"));
        assertFalse(InputValidator.isValidBusinessName("-Farm"));
        assertFalse(InputValidator.isValidBusinessName("Farm; DROP TABLE"));
    }

    @Test
    void bankDetails() {
        assertTrue(InputValidator.isValidBankAccountNumber("1234567890"));
        assertTrue(InputValidator.isValidBankAccountNumber("1234 5678-90"));
        assertFalse(InputValidator.isValidBankAccountNumber("12345"));
        assertFalse(InputValidator.isValidBankAccountNumber("12345678901234567"));
        assertFalse(InputValidator.isValidBankAccountNumber("12345abc90"));
        assertEquals("1234567890", InputValidator.normalizeDigits("1234 5678-90"));
        assertNull(InputValidator.normalizeDigits(" "));

        assertTrue(InputValidator.isValidBranchCode("250655"));
        assertTrue(InputValidator.isValidBranchCode("250 655"));
        assertFalse(InputValidator.isValidBranchCode("2506"));
        assertFalse(InputValidator.isValidBranchCode("2506555"));
        assertFalse(InputValidator.isValidBranchCode("25O655"));
    }

    @Test
    void mediaUrls() {
        assertTrue(InputValidator.isValidMediaUrls("/api/public/images/1"));
        assertTrue(InputValidator.isValidMediaUrls("https://cdn.example.com/a.jpg, /api/public/images/2"));
        assertTrue(InputValidator.isValidMediaUrls("http://localhost:8080/x.png,"));
        assertFalse(InputValidator.isValidMediaUrls("javascript:alert(1)"));
        assertFalse(InputValidator.isValidMediaUrls("//evil.example.com/a.png"));
        assertFalse(InputValidator.isValidMediaUrls("https://ok.example.com/a b.png"));
        assertFalse(InputValidator.isValidMediaUrls("/" + "a".repeat(500)));
    }
}
