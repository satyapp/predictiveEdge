package org.predictiveedge.broker.domain;

import java.util.Objects;

/** Broker-neutral instrument identity paired with the provider's ephemeral streaming token. */
public record LiveMarketDataInstrument(
        Instrument instrument,
        String providerInstrumentId,
        MarketDataInstrumentKind kind) {

    public LiveMarketDataInstrument {
        Objects.requireNonNull(instrument, "Instrument is required");
        if (providerInstrumentId == null || providerInstrumentId.isBlank())
            throw new IllegalArgumentException("Provider instrument id is required");
        providerInstrumentId = providerInstrumentId.trim();
        Objects.requireNonNull(kind, "Market-data instrument kind is required");
    }
}
