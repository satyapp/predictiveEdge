package org.predictiveedge.chart.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface CorporateActionsEvidenceRepository extends JpaRepository<CorporateActionsEvidence, EvidenceKey> {
    List<CorporateActionsEvidence> findByKeySelectionIdAndKeyStage(UUID selectionId, String stage);
}
