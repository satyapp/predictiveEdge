package org.predictiveedge.broker.domain;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Immutable, deterministic set of instruments requested from a live market-data provider. */
public record LiveMarketDataSubscription(
        List<LiveMarketDataInstrument> instruments,
        MarketDataDetail detail) {

    public LiveMarketDataSubscription {
        Objects.requireNonNull(instruments, "Subscription instruments are required");
        Objects.requireNonNull(detail, "Market-data detail is required");
        if (instruments.isEmpty()) throw new IllegalArgumentException("At least one instrument is required");
        var providerIds = new HashSet<String>();
        var identities = new HashSet<Instrument>();
        for (LiveMarketDataInstrument value : instruments) {
            Objects.requireNonNull(value, "Subscription instrument cannot be null");
            if (!providerIds.add(value.providerInstrumentId()))
                throw new IllegalArgumentException("Provider instrument ids must be unique");
            if (!identities.add(value.instrument()))
                throw new IllegalArgumentException("Instrument identities must be unique");
        }
        instruments = List.copyOf(instruments.stream()
                .sorted(Comparator.comparing(value -> value.instrument().exchange() + ":" + value.instrument().symbol()))
                .toList());
    }
}
