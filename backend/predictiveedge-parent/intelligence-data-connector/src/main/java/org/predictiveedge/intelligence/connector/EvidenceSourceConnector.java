package org.predictiveedge.intelligence.connector;

public interface EvidenceSourceConnector {
    EvidenceSource source();

    boolean supports(EvidenceDomain domain);

    Object fetch(EvidenceDomain domain, EvidenceQuery query);
}
