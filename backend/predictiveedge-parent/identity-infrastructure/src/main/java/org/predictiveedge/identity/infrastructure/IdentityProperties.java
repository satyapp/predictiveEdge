package org.predictiveedge.identity.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "predictiveedge.identity")
public class IdentityProperties {
    private String otpSecret;
    private String accessTokenSecret;
    private int otpMinutes = 5;
    private int otpMaxAttempts = 5;
    private int accessTokenMinutes = 15;
    private final Email email = new Email();
    private final Sms sms = new Sms();

    public String getOtpSecret() { return otpSecret; }
    public void setOtpSecret(String otpSecret) { this.otpSecret = otpSecret; }
    public String getAccessTokenSecret() { return accessTokenSecret; }
    public void setAccessTokenSecret(String accessTokenSecret) { this.accessTokenSecret = accessTokenSecret; }
    public int getOtpMinutes() { return otpMinutes; }
    public void setOtpMinutes(int otpMinutes) { this.otpMinutes = otpMinutes; }
    public int getOtpMaxAttempts() { return otpMaxAttempts; }
    public void setOtpMaxAttempts(int otpMaxAttempts) { this.otpMaxAttempts = otpMaxAttempts; }
    public int getAccessTokenMinutes() { return accessTokenMinutes; }
    public void setAccessTokenMinutes(int accessTokenMinutes) { this.accessTokenMinutes = accessTokenMinutes; }
    public Email getEmail() { return email; }
    public Sms getSms() { return sms; }

    public static class Email {
        private String provider = "ses";
        private String fromAddress;
        private String region = "ap-south-1";
        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getFromAddress() { return fromAddress; }
        public void setFromAddress(String fromAddress) { this.fromAddress = fromAddress; }
        public String getRegion() { return region; }
        public void setRegion(String region) { this.region = region; }
    }

    public static class Sms {
        private String provider = "mock";
        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
    }
}
