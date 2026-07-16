package org.predictiveedge.identity.infrastructure;

import java.time.Clock;
import java.time.Duration;

import org.predictiveedge.identity.application.IdentityPolicy;
import org.predictiveedge.identity.application.IdentityPorts.AccessTokenCodec;
import org.predictiveedge.identity.application.IdentityPorts.OtpCodec;
import org.predictiveedge.identity.application.IdentityPorts.PasswordHasher;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
@EnableConfigurationProperties(IdentityProperties.class)
public class IdentityInfrastructureConfiguration {
    @Bean
    Clock identityClock() {
        return Clock.systemUTC();
    }

    @Bean
    IdentityPolicy identityPolicy(IdentityProperties properties) {
        return new IdentityPolicy(
                Duration.ofMinutes(properties.getOtpMinutes()),
                properties.getOtpMaxAttempts(),
                Duration.ofMinutes(properties.getAccessTokenMinutes()));
    }

    @Bean
    PasswordHasher identityPasswordHasher() {
        return new ArgonPasswordHasher();
    }

    @Bean
    OtpCodec identityOtpCodec(IdentityProperties properties) {
        return new HmacOtpCodec(properties.getOtpSecret());
    }

    @Bean
    AccessTokenCodec identityAccessTokenCodec(IdentityProperties properties) {
        return new HmacAccessTokenCodec(properties.getAccessTokenSecret());
    }

    @Bean
    ApplicationRunner rejectMockSmsInProduction(IdentityProperties properties, Environment environment) {
        return arguments -> {
            if (environment.matchesProfiles("prod") && "mock".equalsIgnoreCase(properties.getSms().getProvider())) {
                throw new IllegalStateException("PE_SMS_PROVIDER=mock is forbidden in the prod profile");
            }
        };
    }
}
