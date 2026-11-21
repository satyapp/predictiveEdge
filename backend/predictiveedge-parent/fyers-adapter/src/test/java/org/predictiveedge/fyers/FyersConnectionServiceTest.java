package org.predictiveedge.fyers;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class FyersConnectionServiceTest {
    @Test
    void expiresAtTheNext0630IndiaCutoffWhenIssuedAfterCutoff() {
        assertThat(FyersConnectionService.tokenExpiry(Instant.parse("2026-11-21T08:46:45Z")))
                .isEqualTo(Instant.parse("2026-11-22T01:00:00Z"));
    }

    @Test
    void expiresAtTheSameDay0630IndiaCutoffWhenIssuedBeforeCutoff() {
        assertThat(FyersConnectionService.tokenExpiry(Instant.parse("2026-11-20T23:30:00Z")))
                .isEqualTo(Instant.parse("2026-11-21T01:00:00Z"));
    }
}
