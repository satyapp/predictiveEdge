package org.predictiveedge.guardian.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.predictiveedge.guardian.application.TradeGuardianFailure;
import org.predictiveedge.guardian.application.TradeGuardianService;
import org.predictiveedge.guardian.domain.InstrumentRef;
import org.predictiveedge.guardian.domain.ManualFill;
import org.predictiveedge.guardian.domain.TradeDirection;
import org.predictiveedge.guardian.domain.TradeMonitoringCase;
import org.predictiveedge.identity.api.IdentityBearerTokenFilter;
import org.predictiveedge.identity.api.PlatformSecurityConfiguration;
import org.predictiveedge.identity.application.IdentityService;
import org.predictiveedge.identity.application.IdentityService.AuthenticatedIdentity;
import org.predictiveedge.identity.domain.UserAccount;
import org.predictiveedge.identity.domain.UserState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TradeGuardianController.class)
@ContextConfiguration(classes = TradeGuardianControllerTest.TestApplication.class)
@Import({TradeGuardianController.class, TradeGuardianExceptionHandler.class,
        PlatformSecurityConfiguration.class, IdentityBearerTokenFilter.class})
@ImportAutoConfiguration(exclude = UserDetailsServiceAutoConfiguration.class)
class TradeGuardianControllerTest {
    private static final UUID TRADER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID CASE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-08-01T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TradeGuardianService guardian;

    @MockitoBean
    private IdentityService identities;

    @BeforeEach
    void authenticateToken() {
        UserAccount user = new UserAccount(TRADER_ID, "trader@example.com", "+919999999999", "Trader",
                UserState.ACTIVE, NOW, NOW);
        when(identities.authenticate("valid-token"))
                .thenReturn(new AuthenticatedIdentity(UUID.randomUUID(), user));
    }

    @Test
    void rejectsManualRegistrationWithoutAuthentication() throws Exception {
        mockMvc.perform(post("/api/trade-guardian/v1/monitoring-cases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRegistration()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registersManualTradeForAuthenticatedTrader() throws Exception {
        when(guardian.registerManualTrade(any())).thenReturn(activeCase());

        mockMvc.perform(post("/api/trade-guardian/v1/monitoring-cases")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRegistration()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        "/api/trade-guardian/v1/monitoring-cases/" + CASE_ID))
                .andExpect(jsonPath("$.monitoringCaseId").value(CASE_ID.toString()))
                .andExpect(jsonPath("$.recommendationId").value("recommendation-1"))
                .andExpect(jsonPath("$.state").value("ACTIVE"))
                .andExpect(jsonPath("$.advisoryOnly").value(true));
    }

    @Test
    void returnsGuardianValidationErrorsInsteadOfAuthenticationErrors() throws Exception {
        mockMvc.perform(post("/api/trade-guardian/v1/monitoring-cases")
                        .header("Authorization", "Bearer valid-token")
                        .header("X-Correlation-ID", "correlation-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRegistration().replace("recommendation-1", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GUARDIAN_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.correlationId").value("correlation-1"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("recommendationId"));
    }

    @Test
    void hidesMissingAndOtherTradersCasesBehindNotFound() throws Exception {
        when(guardian.monitoringCase(TRADER_ID, CASE_ID)).thenThrow(new TradeGuardianFailure(
                TradeGuardianFailure.Code.MONITORING_CASE_NOT_FOUND, "Trade monitoring case was not found"));

        mockMvc.perform(get("/api/trade-guardian/v1/monitoring-cases/{id}", CASE_ID)
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GUARDIAN_MONITORING_CASE_NOT_FOUND"));
    }

    @Test
    void exposesAdvisoryLifecycleActionsWithoutAnyExecutionEndpoint() throws Exception {
        TradeMonitoringCase active = activeCase();
        TradeMonitoringCase suspended = active.suspend("Waiting for fresh prices", NOW.plusSeconds(10));
        TradeMonitoringCase resumed = suspended.resume(NOW.plusSeconds(20));
        TradeMonitoringCase completed = resumed.complete(
                new ManualFill(new BigDecimal("4"), new BigDecimal("1535"), NOW.plusSeconds(25), "exit-456"),
                NOW.plusSeconds(30));
        when(guardian.suspendMonitoring(eq(TRADER_ID), eq(CASE_ID), eq("Waiting for fresh prices")))
                .thenReturn(suspended);
        when(guardian.resumeMonitoring(TRADER_ID, CASE_ID)).thenReturn(resumed);
        when(guardian.completeManualTrade(any())).thenReturn(completed);

        mockMvc.perform(post("/api/trade-guardian/v1/monitoring-cases/{id}/actions/suspend", CASE_ID)
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Waiting for fresh prices\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("SUSPENDED"));

        mockMvc.perform(post("/api/trade-guardian/v1/monitoring-cases/{id}/actions/resume", CASE_ID)
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ACTIVE"));

        mockMvc.perform(post("/api/trade-guardian/v1/monitoring-cases/{id}/actions/complete", CASE_ID)
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "quantity": 4,
                                  "averageExitPrice": 1535,
                                  "executedAt": "2026-08-01T10:00:25Z",
                                  "externalExecutionRef": "exit-456"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("COMPLETED"))
                .andExpect(jsonPath("$.exitFill.averagePrice").value(1535))
                .andExpect(jsonPath("$.advisoryOnly").value(true));
    }

    private static String validRegistration() {
        return """
                {
                  "recommendationId": "recommendation-1",
                  "approvedTradePlanRef": "plan-v3",
                  "accountRef": "account-1",
                  "venue": "NSE",
                  "symbol": "RELIANCE",
                  "direction": "LONG",
                  "quantity": 4,
                  "averageEntryPrice": 1500.25,
                  "executedAt": "2026-08-01T09:59:00Z",
                  "externalExecutionRef": "entry-123"
                }
                """;
    }

    private static TradeMonitoringCase activeCase() {
        return TradeMonitoringCase.register(CASE_ID,
                UUID.fromString("30000000-0000-0000-0000-000000000001"), TRADER_ID,
                "recommendation-1", "plan-v3", "account-1", new InstrumentRef("NSE", "RELIANCE"),
                TradeDirection.LONG,
                new ManualFill(new BigDecimal("4"), new BigDecimal("1500.25"), NOW.minusSeconds(60), "entry-123"),
                NOW);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {}
}
