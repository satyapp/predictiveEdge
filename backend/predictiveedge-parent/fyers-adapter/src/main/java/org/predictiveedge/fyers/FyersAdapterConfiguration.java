package org.predictiveedge.fyers;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import org.predictiveedge.broker.connection.CredentialCipher;
import org.predictiveedge.chart.application.LiveMarketScreenSource;
import org.predictiveedge.broker.spi.HistoricalMarketDataProvider;
import org.predictiveedge.broker.spi.LiveMarketDataInstrumentResolver;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "predictiveedge.fyers", name = "enabled", havingValue = "true")
public class FyersAdapterConfiguration {
    @Bean FyersConnectionStore fyersConnectionStore(JdbcTemplate jdbc) { return new JdbcFyersConnectionStore(jdbc); }
    @Bean FyersHttpClient fyersHttpClient(ObjectMapper json,
            @Value("${predictiveedge.fyers.timeout-seconds:30}") long timeout) {
        return new FyersHttpClient(json, Duration.ofSeconds(timeout));
    }
    @Bean FyersConnectionService fyersConnectionService(FyersConnectionStore store, CredentialCipher cipher,
            FyersHttpClient client,
            @Value("${predictiveedge.fyers.enabled:false}") boolean enabled,
            @Value("${predictiveedge.fyers.app-id:}") String appId,
            @Value("${predictiveedge.fyers.secret-id:}") String secretId,
            @Value("${predictiveedge.fyers.redirect-uri:http://localhost:3000/api/fyers/v1/callback}") String redirectUri,
            @Value("${predictiveedge.broker.web-base-url:http://localhost:3000/}") String webBaseUrl) {
        return new FyersConnectionService(store, cipher, client,
                new FyersConnectionService.Settings(enabled, appId, secretId, redirectUri, webBaseUrl),
                Clock.systemUTC(), new SecureRandom());
    }
    @Bean @Primary LiveMarketDataInstrumentResolver fyersInstrumentResolver(FyersHttpClient client) {
        return new FyersInstrumentResolver(client, Clock.systemUTC());
    }
    @Bean @Primary HistoricalMarketDataProvider fyersHistoricalDataProvider(
            FyersConnectionService connections, FyersHttpClient client) {
        return new FyersHistoricalDataProvider(connections, client);
    }
    @Bean FyersMarketDepthEvidenceConnector fyersMarketDepthEvidenceConnector(FyersConnectionService connections,
            FyersHttpClient client) {
        return new FyersMarketDepthEvidenceConnector(connections, client);
    }
    @Bean @Primary LiveMarketScreenSource fyersLiveMarketScreenSource(FyersConnectionService connections,
            FyersHttpClient client, ObjectMapper json,
            @Qualifier("nseMcpLiveMarketScreenSource") Optional<LiveMarketScreenSource> fallback) {
        return new FyersLiveMarketScreenSource(connections, client, json, Clock.systemUTC(), fallback.orElse(null));
    }
}
