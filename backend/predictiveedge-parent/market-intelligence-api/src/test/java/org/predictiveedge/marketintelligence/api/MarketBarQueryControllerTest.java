package org.predictiveedge.marketintelligence.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.predictiveedge.identity.api.IdentityBearerTokenFilter;
import org.predictiveedge.identity.api.PlatformSecurityConfiguration;
import org.predictiveedge.identity.application.IdentityService;
import org.predictiveedge.identity.application.IdentityService.AuthenticatedIdentity;
import org.predictiveedge.identity.domain.UserAccount;
import org.predictiveedge.identity.domain.UserState;
import org.predictiveedge.marketintelligence.application.MarketBarQueryService;
import org.predictiveedge.marketintelligence.application.MarketBarReplayCriteria;
import org.predictiveedge.marketintelligence.application.MarketBarReplayCursor;
import org.predictiveedge.marketintelligence.domain.BarFinalityState;
import org.predictiveedge.marketintelligence.domain.BarInterval;
import org.predictiveedge.marketintelligence.domain.BarTimeframe;
import org.predictiveedge.marketintelligence.domain.ContentHash;
import org.predictiveedge.marketintelligence.domain.MarketBarKey;
import org.predictiveedge.marketintelligence.domain.MarketBarRevision;
import org.predictiveedge.marketintelligence.domain.MarketBarValues;
import org.predictiveedge.marketintelligence.domain.MarketSessionId;
import org.predictiveedge.marketintelligence.domain.ObservationSubject;
import org.predictiveedge.marketintelligence.domain.ObservationSubjectType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MarketBarQueryController.class)
@ContextConfiguration(classes = MarketBarQueryControllerTest.TestApplication.class)
@Import({MarketBarQueryController.class, MarketIntelligenceExceptionHandler.class,
        PlatformSecurityConfiguration.class, IdentityBearerTokenFilter.class})
@ImportAutoConfiguration(exclude = UserDetailsServiceAutoConfiguration.class)
class MarketBarQueryControllerTest {
    private static final UUID USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final Instant START = Instant.parse("2026-08-07T03:45:00Z");

    @Autowired private MockMvc mockMvc;
    @MockitoBean private MarketBarQueryService bars;
    @MockitoBean private IdentityService identities;

    @BeforeEach
    void authenticateToken() {
        var user = new UserAccount(USER_ID, "trader@example.com", "+919999999999", "Trader",
                UserState.ACTIVE, START, START);
        when(identities.authenticate("valid-token"))
                .thenReturn(new AuthenticatedIdentity(UUID.randomUUID(), user));
    }

    @Test
    void rejectsCausalReadsWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/market-intelligence/v1/bars/latest").params(baseParameters()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsTheLatestEligibleRevisionForTheAuthenticatedTenant() throws Exception {
        when(bars.latest(any())).thenReturn(Optional.of(bar()));

        mockMvc.perform(get("/api/market-intelligence/v1/bars/latest")
                        .header("Authorization", "Bearer valid-token").params(baseParameters()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subjectId").value("NSE:INFY"))
                .andExpect(jsonPath("$.revision").value(2))
                .andExpect(jsonPath("$.finalityState").value("CORRECTED"))
                .andExpect(jsonPath("$.close").value(105));

        ArgumentCaptor<MarketBarQueryService.LatestQuery> query =
                ArgumentCaptor.forClass(MarketBarQueryService.LatestQuery.class);
        verify(bars).latest(query.capture());
        assertThat(query.getValue().userId()).isEqualTo(USER_ID);
        assertThat(query.getValue().brokerAccountId()).isEqualTo("ZD123");
    }

    @Test
    void replaysABoundedPageAndReturnsAnOpaqueContinuationCursor() throws Exception {
        var next = new MarketBarReplayCursor(START, "NSE", LocalDate.of(2026, 8, 7), "REGULAR");
        when(bars.replay(any(), any(Integer.class)))
                .thenReturn(new MarketBarQueryService.ReplayPage(List.of(bar()), next));

        mockMvc.perform(get("/api/market-intelligence/v1/bars/replay")
                        .header("Authorization", "Bearer valid-token").params(baseParameters())
                        .param("from", START.toString()).param("to", START.plusSeconds(600).toString())
                        .param("limit", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bars[0].revision").value(2))
                .andExpect(jsonPath("$.nextCursor").isNotEmpty());

        ArgumentCaptor<MarketBarReplayCriteria> criteria = ArgumentCaptor.forClass(MarketBarReplayCriteria.class);
        verify(bars).replay(criteria.capture(), org.mockito.ArgumentMatchers.eq(100));
        assertThat(criteria.getValue().userId()).isEqualTo(USER_ID);
    }

    @Test
    void rejectsInvalidReplayCursorWithStableRequestError() throws Exception {
        mockMvc.perform(get("/api/market-intelligence/v1/bars/replay")
                        .header("Authorization", "Bearer valid-token").params(baseParameters())
                        .param("from", START.toString()).param("to", START.plusSeconds(600).toString())
                        .param("cursor", "not-a-valid-cursor"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MARKET_INTELLIGENCE_INVALID_REQUEST"));

        var invalidTimeframe = baseParameters();
        invalidTimeframe.set("timeframe", "UNKNOWN");
        mockMvc.perform(get("/api/market-intelligence/v1/bars/latest")
                        .header("Authorization", "Bearer valid-token").params(invalidTimeframe))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MARKET_INTELLIGENCE_INVALID_REQUEST"));
    }

    private static org.springframework.util.LinkedMultiValueMap<String, String> baseParameters() {
        var values = new org.springframework.util.LinkedMultiValueMap<String, String>();
        values.add("brokerAccountId", "ZD123");
        values.add("subjectType", "INSTRUMENT");
        values.add("exchange", "NSE");
        values.add("symbol", "INFY");
        values.add("timeframe", "ONE_MINUTE");
        values.add("analysisCutoff", START.plusSeconds(600).toString());
        values.add("knowledgeCutoff", START.plusSeconds(700).toString());
        return values;
    }

    private static MarketBarRevision bar() {
        var key = new MarketBarKey(new ObservationSubject(ObservationSubjectType.INSTRUMENT, "NSE:INFY"),
                new MarketSessionId("NSE", LocalDate.of(2026, 8, 7), "REGULAR"), BarTimeframe.ONE_MINUTE,
                new BarInterval(START, START.plusSeconds(60), false));
        return new MarketBarRevision(key, 2,
                new MarketBarValues(new BigDecimal("100"), new BigDecimal("110"),
                        new BigDecimal("99"), new BigDecimal("105"), 15),
                START.plusSeconds(50), BarFinalityState.CORRECTED, START.plusSeconds(65),
                "LATE_OR_OUT_OF_ORDER_TICK", new ContentHash("a".repeat(64)), "tick-v1", "finality-v1");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {}
}
