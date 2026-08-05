package org.predictiveedge.broker.connection.infrastructure;

import java.net.http.HttpClient;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.predictiveedge.broker.connection.BrokerConnectionService;
import org.predictiveedge.broker.connection.BrokerConnectionStore;
import org.predictiveedge.broker.connection.CredentialCipher;
import org.predictiveedge.broker.connection.UserMarketDataSubscriptionManager;
import org.predictiveedge.broker.zerodha.JdkZerodhaTransport;
import org.predictiveedge.broker.zerodha.JdkZerodhaWebSocketConnector;
import org.predictiveedge.broker.zerodha.ZerodhaLiveMarketDataProvider;
import org.predictiveedge.broker.zerodha.ZerodhaLoginClient;
import org.predictiveedge.broker.zerodha.ZerodhaReconnectPolicy;
import org.predictiveedge.broker.zerodha.ZerodhaSessionClient;
import org.predictiveedge.broker.zerodha.ZerodhaSessionProvider;
import org.predictiveedge.broker.spi.LiveMarketDataProvider;
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
    ZerodhaSessionProvider zerodhaSessionProvider(BrokerConnectionStore store, CredentialCipher cipher,
            @Value("${predictiveedge.broker.zerodha.api-key:}") String apiKey) {
        if (apiKey == null || apiKey.isBlank()) return context -> {
            throw new IllegalStateException("Zerodha API key is not configured");
        };
        return new StoredZerodhaSessionProvider(store, cipher, apiKey, Clock.systemUTC());
    }

    @Bean(destroyMethod = "shutdown")
    ScheduledExecutorService zerodhaMarketDataScheduler(
            @Value("${predictiveedge.broker.zerodha.stream.scheduler-threads:2}") int threads) {
        if (threads < 1) throw new IllegalArgumentException("Zerodha stream scheduler requires at least one thread");
        return Executors.newScheduledThreadPool(threads, runnable -> {
            var thread = new Thread(runnable, "zerodha-market-data");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Bean
    ZerodhaLiveMarketDataProvider zerodhaLiveMarketDataProvider(
            ZerodhaSessionProvider sessions,
            ScheduledExecutorService zerodhaMarketDataScheduler,
            ObjectMapper json,
            @Value("${predictiveedge.broker.zerodha.stream.connect-timeout-seconds:10}") long connectTimeoutSeconds,
            @Value("${predictiveedge.broker.zerodha.stream.initial-reconnect-milliseconds:500}") long initialReconnectMilliseconds,
            @Value("${predictiveedge.broker.zerodha.stream.maximum-reconnect-seconds:30}") long maximumReconnectSeconds,
            @Value("${predictiveedge.broker.zerodha.stream.maximum-reconnect-attempts:8}") int maximumReconnectAttempts,
            @Value("${predictiveedge.broker.zerodha.stream.stale-after-seconds:15}") long staleAfterSeconds,
            @Value("${predictiveedge.broker.zerodha.stream.maximum-frame-bytes:2097152}") int maximumFrameBytes) {
        var connectTimeout = Duration.ofSeconds(connectTimeoutSeconds);
        var client = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        var connector = new JdkZerodhaWebSocketConnector(client, connectTimeout);
        var policy = new ZerodhaReconnectPolicy(
                Duration.ofMillis(initialReconnectMilliseconds),
                Duration.ofSeconds(maximumReconnectSeconds),
                maximumReconnectAttempts,
                Duration.ofSeconds(staleAfterSeconds),
                maximumFrameBytes);
        return new ZerodhaLiveMarketDataProvider(sessions, connector, zerodhaMarketDataScheduler,
                Clock.systemUTC(), json, policy);
    }

    @Bean(destroyMethod = "close")
    UserMarketDataSubscriptionManager userMarketDataSubscriptionManager(
            LiveMarketDataProvider liveMarketDataProvider) {
        return new UserMarketDataSubscriptionManager(liveMarketDataProvider);
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
