package org.predictiveedge.chart.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface InstitutionalFlowsEvidenceRepository extends JpaRepository<InstitutionalFlowsEvidence, EvidenceKey> {
    List<InstitutionalFlowsEvidence> findByKeySelectionIdAndKeyStage(UUID selectionId, String stage);
}
