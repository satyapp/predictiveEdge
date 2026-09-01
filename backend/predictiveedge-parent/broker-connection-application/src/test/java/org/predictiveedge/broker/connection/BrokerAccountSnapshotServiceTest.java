package org.predictiveedge.broker.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.predictiveedge.broker.domain.BrokerAccountSnapshot;
import org.predictiveedge.broker.domain.BrokerFundsSegment;
import org.predictiveedge.broker.domain.BrokerId;
import org.predictiveedge.broker.spi.BrokerContext;

class BrokerAccountSnapshotServiceTest {
    private static final UUID USER_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-01T04:00:00Z");

    @Test
    void capturesProviderFactsIntoTheImmutableEvidencePort() {
        var snapshot = snapshot("ZD123");
        var recorded = new java.util.ArrayList<BrokerAccountSnapshot>();
        BrokerAccountEvidencePort port = new BrokerAccountEvidencePort() {
            @Override public BrokerAccountEvidence publish(UUID userId, BrokerAccountSnapshot value) {
                assertThat(userId).isEqualTo(USER_ID);
                recorded.add(value);
                return new BrokerAccountEvidence(UUID.randomUUID(), value, "a".repeat(64));
            }
            @Override public Optional<BrokerAccountEvidence> latestAtOrBefore(
                    UUID userId, String accountId, Instant cutoff) { return Optional.empty(); }
        };
        var service = new BrokerAccountSnapshotService(context -> snapshot, port);

        var result = service.capture(BrokerContext.withoutCredentials(USER_ID, "ZD123"));

        assertThat(recorded).containsExactly(snapshot);
        assertThat(result.evidenceHash()).isEqualTo("a".repeat(64));
    }

    @Test
    void rejectsCrossAccountProviderData() {
        var service = new BrokerAccountSnapshotService(context -> snapshot("OTHER"), new NoopEvidence());

        assertThatThrownBy(() -> service.capture(BrokerContext.withoutCredentials(USER_ID, "ZD123")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("does not match");
    }

    private static BrokerAccountSnapshot snapshot(String accountId) {
        var funds = new BrokerFundsSegment("equity", true, new BigDecimal("1000"),
                Map.of("live_balance", new BigDecimal("1000")), Map.of("debits", BigDecimal.ZERO));
        return new BrokerAccountSnapshot(new BrokerId("zerodha"), accountId, Map.of("equity", funds),
                List.of(), List.of(), List.of(), NOW, NOW);
    }

    private static final class NoopEvidence implements BrokerAccountEvidencePort {
        @Override public BrokerAccountEvidence publish(UUID userId, BrokerAccountSnapshot snapshot) {
            throw new AssertionError("Cross-account evidence must not be published");
        }
        @Override public Optional<BrokerAccountEvidence> latestAtOrBefore(
                UUID userId, String accountId, Instant cutoff) { return Optional.empty(); }
    }
}
