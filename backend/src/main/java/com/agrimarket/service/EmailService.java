package com.agrimarket.service;

public interface EmailService {
    void send(String to, String subject, String plainTextBody, String htmlBody);
}

