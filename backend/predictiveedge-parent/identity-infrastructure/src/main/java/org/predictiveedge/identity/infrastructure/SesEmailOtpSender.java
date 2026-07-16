package org.predictiveedge.identity.infrastructure;

import org.predictiveedge.identity.application.IdentityPorts.EmailOtpSender;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

@Component
@ConditionalOnProperty(prefix = "predictiveedge.identity.email", name = "provider", havingValue = "ses", matchIfMissing = true)
public class SesEmailOtpSender implements EmailOtpSender, AutoCloseable {
    private final IdentityProperties properties;
    private final SesV2Client client;

    public SesEmailOtpSender(IdentityProperties properties) {
        this.properties = properties;
        this.client = SesV2Client.builder().region(Region.of(properties.getEmail().getRegion())).build();
    }

    @Override
    public void send(String email, String displayName, String otp, int expiresInMinutes) {
        String safeName = displayName.replace("<", "&lt;").replace(">", "&gt;");
        String html = """
                <html><body style="font-family:Arial,sans-serif;color:#17324d">
                <h2>Verify your PredictiveEdge email</h2>
                <p>Hello %s,</p><p>Your verification code is:</p>
                <p style="font-size:28px;font-weight:bold;letter-spacing:6px">%s</p>
                <p>This code expires in %d minutes. Do not share it.</p>
                </body></html>
                """.formatted(safeName, otp, expiresInMinutes);
        Message message = Message.builder()
                .subject(Content.builder().data("Your PredictiveEdge verification code").build())
                .body(Body.builder().html(Content.builder().data(html).build()).build())
                .build();
        client.sendEmail(SendEmailRequest.builder()
                .fromEmailAddress(properties.getEmail().getFromAddress())
                .destination(Destination.builder().toAddresses(email).build())
                .content(EmailContent.builder().simple(message).build())
                .build());
    }

    @Override
    public void close() {
        client.close();
    }
}
