package org.predictiveedge.broker.connection.infrastructure;

import java.net.http.HttpClient;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;

import org.predictiveedge.broker.connection.BrokerConnectionService;
import org.predictiveedge.broker.connection.BrokerConnectionStore;
import org.predictiveedge.broker.connection.CredentialCipher;
import org.predictiveedge.broker.zerodha.JdkZerodhaTransport;
import org.predictiveedge.broker.zerodha.ZerodhaLoginClient;
import org.predictiveedge.broker.zerodha.ZerodhaSessionClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
public class BrokerConnectionInfrastructureConfiguration {
    @Bean
    BrokerConnectionStore brokerConnectionStore(JdbcTemplate jdbc) {
        return new JdbcBrokerConnectionStore(jdbc);
    }

    @Bean
    CredentialCipher brokerCredentialCipher(
            @Value("${predictiveedge.broker.credential-key}") String masterSecret) {
        return new AesGcmCredentialCipher(masterSecret, new SecureRandom());
    }

    @Bean
    BrokerConnectionService brokerConnectionService(
            BrokerConnectionStore store,
            CredentialCipher cipher,
            ObjectMapper json,
            @Value("${predictiveedge.broker.zerodha.api-key:}") String apiKey,
            @Value("${predictiveedge.broker.zerodha.api-secret:}") String apiSecret,
            @Value("${predictiveedge.broker.web-base-url:http://localhost:3000/}") String webBaseUrl,
            @Value("${predictiveedge.broker.connection-state-minutes:10}") long stateMinutes,
            @Value("${predictiveedge.broker.browser-lease-seconds:120}") long leaseSeconds,
            @Value("${predictiveedge.broker.browser-close-grace-seconds:30}") long closeGraceSeconds) {
        var transport = new JdkZerodhaTransport(HttpClient.newBuilder().build());
        var settings = new BrokerConnectionService.Settings(apiKey, apiSecret, webBaseUrl,
                Duration.ofMinutes(stateMinutes), Duration.ofSeconds(leaseSeconds),
                Duration.ofSeconds(closeGraceSeconds));
        return new BrokerConnectionService(store, cipher, new ZerodhaLoginClient(transport, json),
                new ZerodhaSessionClient(transport),
                settings, Clock.systemUTC(), new SecureRandom());
    }
}
