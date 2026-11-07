package com.agrimarket.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Abuse protection for login, sign-up, password reset and OTP ({@code app.security.*}).
 *
 * <p>Defaults are the production values; the test profile switches per-IP limits off because
 * integration tests make many calls from one address.
 */
@Component
@ConfigurationProperties(prefix = "app.security")
public class AuthSecurityProperties {

    /**
     * Proxies in front of the API that append to {@code X-Forwarded-For}. Railway only = 1;
     * load balancer + nginx = 2; nothing in front = 0 (header ignored).
     */
    private int trustedProxyHops = 1;

    private final RateLimit rateLimit = new RateLimit();
    private final Login login = new Login();
    private final PerEmail perEmail = new PerEmail();

    public int getTrustedProxyHops() {
        return trustedProxyHops;
    }

    public void setTrustedProxyHops(int trustedProxyHops) {
        this.trustedProxyHops = trustedProxyHops;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public Login getLogin() {
        return login;
    }

    public PerEmail getPerEmail() {
        return perEmail;
    }

    /** Per client IP limits on the public auth endpoints (429 + Retry-After when exceeded). */
    public static class RateLimit {
        private boolean enabled = true;
        private int loginPerMinute = 10;
        private int loginPerHour = 100;
        private int registerPerHour = 10;
        private int forgotPasswordPer15Min = 5;
        private int resetPasswordPer15Min = 10;
        private int otpRequestPer15Min = 5;
        private int otpVerifyPer15Min = 20;
        private int changePasswordPer15Min = 10;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getLoginPerMinute() {
            return loginPerMinute;
        }

        public void setLoginPerMinute(int loginPerMinute) {
            this.loginPerMinute = loginPerMinute;
        }

        public int getLoginPerHour() {
            return loginPerHour;
        }

        public void setLoginPerHour(int loginPerHour) {
            this.loginPerHour = loginPerHour;
        }

        public int getRegisterPerHour() {
            return registerPerHour;
        }

        public void setRegisterPerHour(int registerPerHour) {
            this.registerPerHour = registerPerHour;
        }

        public int getForgotPasswordPer15Min() {
            return forgotPasswordPer15Min;
        }

        public void setForgotPasswordPer15Min(int forgotPasswordPer15Min) {
            this.forgotPasswordPer15Min = forgotPasswordPer15Min;
        }

        public int getResetPasswordPer15Min() {
            return resetPasswordPer15Min;
        }

        public void setResetPasswordPer15Min(int resetPasswordPer15Min) {
            this.resetPasswordPer15Min = resetPasswordPer15Min;
        }

        public int getOtpRequestPer15Min() {
            return otpRequestPer15Min;
        }

        public void setOtpRequestPer15Min(int otpRequestPer15Min) {
            this.otpRequestPer15Min = otpRequestPer15Min;
        }

        public int getOtpVerifyPer15Min() {
            return otpVerifyPer15Min;
        }

        public void setOtpVerifyPer15Min(int otpVerifyPer15Min) {
            this.otpVerifyPer15Min = otpVerifyPer15Min;
        }

        public int getChangePasswordPer15Min() {
            return changePasswordPer15Min;
        }

        public void setChangePasswordPer15Min(int changePasswordPer15Min) {
            this.changePasswordPer15Min = changePasswordPer15Min;
        }
    }

    /** Temporary account lockout after repeated wrong passwords. */
    public static class Login {
        private int maxFailures = 5;
        private int lockMinutes = 15;

        public int getMaxFailures() {
            return maxFailures;
        }

        public void setMaxFailures(int maxFailures) {
            this.maxFailures = maxFailures;
        }

        public int getLockMinutes() {
            return lockMinutes;
        }

        public void setLockMinutes(int lockMinutes) {
            this.lockMinutes = lockMinutes;
        }
    }

    /**
     * Per email address caps on outgoing security emails, so one inbox cannot be flooded. Over the
     * cap the request is answered exactly as usual but nothing is sent (no enumeration).
     */
    public static class PerEmail {
        private int forgotPasswordPerHour = 3;
        private int otpSendsPer15Min = 3;

        public int getForgotPasswordPerHour() {
            return forgotPasswordPerHour;
        }

        public void setForgotPasswordPerHour(int forgotPasswordPerHour) {
            this.forgotPasswordPerHour = forgotPasswordPerHour;
        }

        public int getOtpSendsPer15Min() {
            return otpSendsPer15Min;
        }

        public void setOtpSendsPer15Min(int otpSendsPer15Min) {
            this.otpSendsPer15Min = otpSendsPer15Min;
        }
    }
}
