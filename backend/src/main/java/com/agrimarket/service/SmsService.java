package com.agrimarket.service;

import com.agrimarket.config.SmsProperties;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Sends SMS via Twilio (Wheel Hub pattern). No-op when disabled or not configured.
 */
@Service
public class SmsService {

    private static final Logger log = LoggerFactory.getLogger(SmsService.class);

    private final SmsProperties props;
    private volatile boolean initialized = false;

    public SmsService(SmsProperties props) {
        this.props = props;
    }

    public boolean isConfigured() {
        if (!props.isEnabled()) {
            return false;
        }
        String sid = trim(props.getTwilioAccountSid());
        String token = trim(props.getTwilioAuthToken());
        if (sid.isEmpty() || token.isEmpty()) {
            return false;
        }
        return !trim(props.getFromNumber()).isEmpty() || !trim(props.getMessagingServiceSid()).isEmpty();
    }

    public void sendSms(String toRaw, String body) {
        if (!isConfigured()) {
            return;
        }
        if (toRaw == null || toRaw.isBlank() || body == null || body.isBlank()) {
            return;
        }
        String to = normalizePhone(toRaw);
        if (to == null) {
            log.debug("SMS skipped: could not normalize phone {}", toRaw);
            return;
        }
        ensureInit();
        try {
            createAndSend(new PhoneNumber(to), body);
            log.info("SMS sent to {}", to);
        } catch (Exception e) {
            log.warn("SMS failed to {}: {}", to, e.getMessage());
        }
    }

    private Message createAndSend(PhoneNumber to, String body) {
        String mg = trim(props.getMessagingServiceSid());
        if (!mg.isEmpty()) {
            return Message.creator(to, mg, body).create();
        }
        return Message.creator(to, new PhoneNumber(trim(props.getFromNumber())), body).create();
    }

    private void ensureInit() {
        if (initialized) {
            return;
        }
        synchronized (this) {
            if (initialized) {
                return;
            }
            Twilio.init(trim(props.getTwilioAccountSid()), trim(props.getTwilioAuthToken()));
            initialized = true;
            log.info("Twilio SMS initialized");
        }
    }

    String normalizePhone(String raw) {
        String digits = raw.replaceAll("[^0-9+]", "");
        if (digits.startsWith("00")) {
            digits = "+" + digits.substring(2);
        }
        if (digits.startsWith("+")) {
            return digits.length() >= 10 ? digits : null;
        }
        String cc = trim(props.getDefaultCountryCode());
        if (cc.isEmpty()) {
            cc = "+27";
        }
        if (!cc.startsWith("+")) {
            cc = "+" + cc;
        }
        if (digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        String full = cc + digits;
        return full.length() >= 10 ? full : null;
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
