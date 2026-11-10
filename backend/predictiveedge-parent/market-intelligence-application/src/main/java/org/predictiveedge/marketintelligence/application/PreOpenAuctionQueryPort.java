package org.predictiveedge.marketintelligence.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface PreOpenAuctionQueryPort {
    Optional<PreOpenAuctionSnapshot> latestAtOrBefore(
            UUID userId,
            String brokerAccountId,
            String venue,
            String symbol,
            LocalDate tradingDate,
            Instant analysisCutoff,
            Instant knowledgeCutoff);
}
