package org.predictiveedge.intelligence.connector;

import java.util.Objects;

public final class DerivedEvidenceConnector implements EvidenceSourceConnector {
    private final EvidenceSource source;
    private final EvidenceDomain domain;
    private final DerivedEvidencePort port;

    public DerivedEvidenceConnector(EvidenceSource source, EvidenceDomain domain, DerivedEvidencePort port) {
        if (source != EvidenceSource.RAW_DATA_ENGINE && source != EvidenceSource.PATTERN_DETECTION_ENGINE) {
            throw new IllegalArgumentException("Derived connector requires an engine source");
        }
        this.source = source;
        this.domain = Objects.requireNonNull(domain, "Derived domain is required");
        this.port = Objects.requireNonNull(port, "Derived evidence port is required");
    }

    @Override public EvidenceSource source() { return source; }
    @Override public boolean supports(EvidenceDomain requested) { return domain == requested; }

    @Override
    public Object fetch(EvidenceDomain requested, EvidenceQuery query) {
        if (!supports(requested)) throw new IllegalArgumentException("Unsupported derived domain: " + requested);
        return port.derive(query);
    }
}
