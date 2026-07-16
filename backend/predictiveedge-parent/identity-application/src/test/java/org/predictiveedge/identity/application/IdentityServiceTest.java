package org.predictiveedge.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.predictiveedge.identity.application.IdentityPorts.AccessTokenCodec;
import org.predictiveedge.identity.application.IdentityPorts.DeliveryReceipt;
import org.predictiveedge.identity.application.IdentityPorts.EmailOtpSender;
import org.predictiveedge.identity.application.IdentityPorts.OtpCodec;
import org.predictiveedge.identity.application.IdentityPorts.PasswordHasher;
import org.predictiveedge.identity.application.IdentityPorts.RegistrationRecord;
import org.predictiveedge.identity.application.IdentityPorts.SmsOtpSender;
import org.predictiveedge.identity.application.IdentityPorts.Store;
import org.predictiveedge.identity.application.IdentityPorts.VerificationOutcome;
import org.predictiveedge.identity.domain.IdentityFailure;
import org.predictiveedge.identity.domain.OtpChannel;

class IdentityServiceTest {
    private final Store store = mock(Store.class);
    private final PasswordHasher passwords = mock(PasswordHasher.class);
    private final OtpCodec otpCodec = mock(OtpCodec.class);
    private final AccessTokenCodec tokens = mock(AccessTokenCodec.class);
    private final EmailOtpSender emailSender = mock(EmailOtpSender.class);
    private final SmsOtpSender smsSender = mock(SmsOtpSender.class);
    private IdentityService service;

    @BeforeEach
    void setUp() {
        when(passwords.hash(anyString())).thenReturn("dummy-argon-hash");
        service = new IdentityService(
                store, passwords, otpCodec, tokens, emailSender, smsSender,
                new IdentityPolicy(Duration.ofMinutes(5), 5, Duration.ofMinutes(15)),
                Clock.fixed(Instant.parse("2026-07-14T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void registrationPersistsHashesAndRoutesBothOtpChannels() {
        when(passwords.hash("correct horse battery")).thenReturn("argon-hash");
        when(otpCodec.generate()).thenReturn("111111", "222222");
        when(otpCodec.hash(any(), eq(OtpChannel.EMAIL), eq("111111"))).thenReturn("email-hash");
        when(otpCodec.hash(any(), eq(OtpChannel.MOBILE), eq("222222"))).thenReturn("mobile-hash");
        when(store.createRegistration(any())).thenAnswer(invocation -> {
            IdentityPorts.NewRegistration request = invocation.getArgument(0);
            return new RegistrationRecord(request.userId(), request.verificationSessionId(), true);
        });
        when(smsSender.send("+919876543210", "222222", 5)).thenReturn(new DeliveryReceipt("mock-1", "222222"));

        var result = service.register(new IdentityService.RegisterCommand(
                "Satya", "SATYA@example.com", "+919876543210", "correct horse battery"));

        assertThat(result.developmentMobileOtp()).isEqualTo("222222");
        assertThat(result.maskedEmail()).isEqualTo("S***@example.com");
        verify(emailSender).send("satya@example.com", "Satya", "111111", 5);
        verify(smsSender).send("+919876543210", "222222", 5);
    }

    @Test
    void invalidOtpUsesStableDomainFailure() {
        when(otpCodec.hash(any(), eq(OtpChannel.EMAIL), eq("000000"))).thenReturn("bad-hash");
        when(store.verifyOtp(any(), eq(OtpChannel.EMAIL), eq("bad-hash"), any(), anyInt()))
                .thenReturn(VerificationOutcome.INVALID);

        assertThatThrownBy(() -> service.verifyOtp(java.util.UUID.randomUUID(), OtpChannel.EMAIL, "000000"))
                .isInstanceOf(IdentityFailure.class)
                .extracting(failure -> ((IdentityFailure) failure).code())
                .isEqualTo(IdentityFailure.Code.OTP_INVALID);
    }
}
