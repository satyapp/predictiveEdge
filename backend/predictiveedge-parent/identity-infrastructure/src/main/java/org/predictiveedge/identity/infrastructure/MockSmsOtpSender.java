package org.predictiveedge.identity.infrastructure;

import java.util.UUID;

import org.predictiveedge.identity.application.IdentityPorts.DeliveryReceipt;
import org.predictiveedge.identity.application.IdentityPorts.SmsOtpSender;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "predictiveedge.identity.sms", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockSmsOtpSender implements SmsOtpSender {
    @Override
    public DeliveryReceipt send(String mobileNumber, String otp, int expiresInMinutes) {
        return new DeliveryReceipt("mock-sms-" + UUID.randomUUID(), otp);
    }
}
