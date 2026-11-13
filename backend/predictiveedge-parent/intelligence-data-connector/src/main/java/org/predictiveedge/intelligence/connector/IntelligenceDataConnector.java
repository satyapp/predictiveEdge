package org.predictiveedge.intelligence.connector;

import java.util.List;
import org.predictiveedge.decision.domain.DecisionResourceType;

public interface IntelligenceDataConnector {
    List<ProviderEvidence> fetch(EvidenceDomain domain, EvidenceQuery query);

    ResourceEvidenceBundle fetchForResource(DecisionResourceType resourceType, EvidenceQuery query);
}
