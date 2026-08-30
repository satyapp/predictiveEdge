package org.predictiveedge.decision.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.predictiveedge.decision.domain.AssessmentReadiness;
import org.predictiveedge.decision.domain.ExecutionContext;
import org.predictiveedge.decision.domain.ExecutionEvidenceSnapshot;
import org.predictiveedge.decision.domain.GateDisposition;
import org.predictiveedge.decision.domain.InstrumentRef;
import org.predictiveedge.decision.domain.PortfolioSnapshot;
import org.predictiveedge.decision.domain.RiskSnapshot;
import org.predictiveedge.decision.domain.ShadowScope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class JdbcDecisionSafetySnapshotStoreTest {
    private static final Instant NOW = Instant.parse("2026-08-15T05:00:00Z");
    private static final UUID USER = UUID.randomUUID();
    private static final InstrumentRef EQUITY = new InstrumentRef("NSE", "INE002A01018");
    private static final ShadowScope SCOPE = new ShadowScope(USER, EQUITY);
    private static final String HASH = "e".repeat(64);

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void appendsAndCausallyReadsAllThreeSafetySnapshots() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ObjectMapper json = mock(ObjectMapper.class);
        RiskSnapshot risk = risk();
        PortfolioSnapshot portfolio = portfolio();
        ExecutionEvidenceSnapshot execution = execution();
        when(json.writeValueAsString(any())).thenReturn("{}");
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(json.readValue("{}", RiskSnapshot.class)).thenReturn(risk);
        when(json.readValue("{}", PortfolioSnapshot.class)).thenReturn(portfolio);
        when(json.readValue("{}", ExecutionEvidenceSnapshot.class)).thenReturn(execution);
        ResultSet result = mock(ResultSet.class);
        when(result.getString("snapshot_json")).thenReturn("{}");
        var queries = new ArrayList<String>();
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            queries.add(sql);
            assertThat(sql).contains("analysis_cutoff<=?", "knowledge_cutoff<=?", "available_at<=?");
            RowMapper mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(result, 0));
        });
        var store = new JdbcDecisionSafetySnapshotStore(jdbc, json);

        assertThat(store.append(risk)).isTrue();
        assertThat(store.append(portfolio)).isTrue();
        assertThat(store.append(execution)).isTrue();
        assertThat(store.findLatestRisk(SCOPE, NOW)).contains(risk);
        assertThat(store.findLatestPortfolio(SCOPE, NOW)).contains(portfolio);
        assertThat(store.findLatestEvidence(SCOPE, NOW)).contains(execution);
        assertThat(store.findLatest(SCOPE, NOW)).isEqualTo(execution.context());
        assertThat(queries).anySatisfy(sql -> assertThat(sql).contains("decision.risk_snapshot"));
        assertThat(queries).anySatisfy(sql -> assertThat(sql).contains("decision.portfolio_snapshot"));
        assertThat(queries).anySatisfy(sql -> assertThat(sql).contains("decision.execution_snapshot"));
    }

    private static RiskSnapshot risk() {
        return new RiskSnapshot("risk-1", USER, EQUITY, AssessmentReadiness.READY, GateDisposition.PASS,
                money(100000), money(1000), money(2000), money(500), money(5000), money(25000), true,
                "risk-v1", before(3), before(2), before(1), NOW.plusSeconds(30), List.of("account:1"), HASH);
    }

    private static PortfolioSnapshot portfolio() {
        return new PortfolioSnapshot("portfolio-1", USER, EQUITY, AssessmentReadiness.READY, GateDisposition.PASS,
                money(80000), money(20000), money(20000), BigDecimal.TEN, money(20000), money(20), money(20),
                money(25), 1, "portfolio-v1", before(3), before(2), before(1), NOW.plusSeconds(30),
                List.of("account:1"), HASH);
    }

    private static ExecutionEvidenceSnapshot execution() {
        ExecutionContext context = new ExecutionContext(before(1), money(99), money(101), "depth:1", 1,
                money(101), BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, 100,
                true, true, NOW.plusSeconds(10));
        return new ExecutionEvidenceSnapshot("execution-1", USER, EQUITY, AssessmentReadiness.READY, context,
                before(3), before(2), before(1), List.of("depth:1"), HASH);
    }

    private static Instant before(long seconds) { return NOW.minusSeconds(seconds); }
    private static BigDecimal money(long value) { return BigDecimal.valueOf(value); }
}
