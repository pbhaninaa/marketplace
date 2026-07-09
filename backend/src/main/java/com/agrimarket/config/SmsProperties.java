package com.agrimarket.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Twilio SMS (Wheel Hub pattern). When disabled or incomplete, SMS is a no-op.
 */
@Component
@ConfigurationProperties(prefix = "app.sms")
@Getter
@Setter
public class SmsProperties {
    private boolean enabled = false;
    private String twilioAccountSid = "";
    private String twilioAuthToken = "";
    /** E.164 sender, e.g. +27123456789 */
    private String fromNumber = "";
    /** Optional Messaging Service SID (MG...) instead of fromNumber. */
    private String messagingServiceSid = "";
    /** Default country code prepended when phone has no + (e.g. +27). */
    private String defaultCountryCode = "+27";
}
