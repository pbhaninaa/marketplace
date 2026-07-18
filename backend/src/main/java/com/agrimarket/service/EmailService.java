package com.agrimarket.service;

public interface EmailService {
    void send(String to, String subject, String plainTextBody, String htmlBody);

    /**
     * Sends an email from the address associated with the supplied purpose.
     * The default keeps existing EmailService implementations source-compatible.
     */
    default void send(
            EmailPurpose purpose,
            String to,
            String subject,
            String plainTextBody,
            String htmlBody) {
        send(to, subject, plainTextBody, htmlBody);
    }
}

