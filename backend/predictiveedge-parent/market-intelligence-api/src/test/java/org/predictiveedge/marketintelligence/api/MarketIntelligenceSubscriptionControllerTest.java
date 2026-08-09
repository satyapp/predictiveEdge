package org.predictiveedge.marketintelligence.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.predictiveedge.broker.connection.UserMarketDataSubscriptionStatus;
import org.predictiveedge.broker.domain.Instrument;
import org.predictiveedge.broker.domain.LiveMarketDataInstrument;
import org.predictiveedge.broker.domain.LiveMarketDataSubscription;
import org.predictiveedge.broker.domain.MarketDataDetail;
import org.predictiveedge.broker.domain.MarketDataInstrumentKind;
import org.predictiveedge.broker.domain.MarketDataStreamState;
import org.predictiveedge.identity.api.IdentityBearerTokenFilter;
import org.predictiveedge.identity.api.PlatformSecurityConfiguration;
import org.predictiveedge.identity.application.IdentityService;
import org.predictiveedge.identity.application.IdentityService.AuthenticatedIdentity;
import org.predictiveedge.identity.domain.UserAccount;
import org.predictiveedge.identity.domain.UserState;
import org.predictiveedge.marketintelligence.application.UserMarketIntelligenceSubscriptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MarketIntelligenceSubscriptionController.class)
@ContextConfiguration(classes = MarketIntelligenceSubscriptionControllerTest.TestApplication.class)
@Import({MarketIntelligenceSubscriptionController.class, MarketIntelligenceExceptionHandler.class,
        PlatformSecurityConfiguration.class, IdentityBearerTokenFilter.class})
@ImportAutoConfiguration(exclude = UserDetailsServiceAutoConfiguration.class)
class MarketIntelligenceSubscriptionControllerTest {
    private static final UUID USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-08-09T06:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserMarketIntelligenceSubscriptionService subscriptions;

    @MockitoBean
    private IdentityService identities;

    @BeforeEach
    void authenticateToken() {
        var user = new UserAccount(USER_ID, "trader@example.com", "+919999999999", "Trader",
                UserState.ACTIVE, NOW, NOW);
        when(identities.authenticate("valid-token"))
                .thenReturn(new AuthenticatedIdentity(UUID.randomUUID(), user));
    }

    @Test
    void rejectsSubscriptionCommandsWithoutAuthentication() throws Exception {
        mockMvc.perform(put("/api/market-intelligence/v1/subscription")
                        .contentType(MediaType.APPLICATION_JSON).content(validRequest()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void startsTheCanonicalStreamOnlyForTheAuthenticatedUser() throws Exception {
        when(subscriptions.subscribe(any(), any())).thenReturn(connectedStatus());

        mockMvc.perform(put("/api/market-intelligence/v1/subscription")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON).content(validRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brokerAccountId").value("ZD123"))
                .andExpect(jsonPath("$.state").value("CONNECTED"))
                .andExpect(jsonPath("$.detail").value("FULL"))
                .andExpect(jsonPath("$.instruments[0].exchange").value("NSE"))
                .andExpect(jsonPath("$.instruments[0].symbol").value("INFY"))
                .andExpect(jsonPath("$.instruments[0].kind").value("EQUITY"))
                .andExpect(jsonPath("$.instruments[0].providerInstrumentId").doesNotExist());

        verify(subscriptions).subscribe(
                argThat(context -> context.userId().equals(USER_ID)
                        && context.brokerAccountId().equals("ZD123")
                        && context.credentialReference() == null),
                eq(List.of(new Instrument("NSE", "INFY"))));
    }

    @Test
    void validatesNestedInstrumentInputWithAStableErrorShape() throws Exception {
        mockMvc.perform(put("/api/market-intelligence/v1/subscription")
                        .header("Authorization", "Bearer valid-token")
                        .header("X-Correlation-ID", "correlation-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"brokerAccountId":"ZD123","instruments":[{"exchange":"NSE","symbol":""}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MARKET_INTELLIGENCE_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.correlationId").value("correlation-1"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("instruments[0].symbol"));

        mockMvc.perform(put("/api/market-intelligence/v1/subscription")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"brokerAccountId\":\"ZD123\",\"instruments\":[null]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MARKET_INTELLIGENCE_VALIDATION_FAILED"));

        mockMvc.perform(put("/api/market-intelligence/v1/subscription")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MARKET_INTELLIGENCE_INVALID_REQUEST"));
    }

    @Test
    void returnsAndIdempotentlyStopsOnlyTheUsersCurrentSubscription() throws Exception {
        when(subscriptions.status(USER_ID)).thenReturn(Optional.of(connectedStatus()));

        mockMvc.perform(get("/api/market-intelligence/v1/subscription")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("CONNECTED"));

        mockMvc.perform(delete("/api/market-intelligence/v1/subscription")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isNoContent());

        verify(subscriptions).status(USER_ID);
        verify(subscriptions).unsubscribe(USER_ID);
    }

    @Test
    void reportsNotFoundWhenTheUserHasNoSubscription() throws Exception {
        when(subscriptions.status(USER_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/market-intelligence/v1/subscription")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isNotFound());
    }

    private static UserMarketDataSubscriptionStatus connectedStatus() {
        var instrument = new LiveMarketDataInstrument(new Instrument("NSE", "INFY"), "408065",
                MarketDataInstrumentKind.EQUITY);
        return new UserMarketDataSubscriptionStatus(USER_ID, "ZD123",
                new LiveMarketDataSubscription(List.of(instrument), MarketDataDetail.FULL),
                MarketDataStreamState.CONNECTED);
    }

    private static String validRequest() {
        return """
                {"brokerAccountId":"ZD123","instruments":[{"exchange":"nse","symbol":"infy"}]}
                """;
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {}
}
