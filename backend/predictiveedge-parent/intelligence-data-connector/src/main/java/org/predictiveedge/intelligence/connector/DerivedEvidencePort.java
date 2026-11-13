package org.predictiveedge.intelligence.connector;

@FunctionalInterface
public interface DerivedEvidencePort {
    Object derive(EvidenceQuery query);
}
