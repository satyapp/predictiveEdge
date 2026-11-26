package org.predictiveedge.chart.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface TechnicalAnalysisEvidenceRepository extends JpaRepository<TechnicalAnalysisEvidence, EvidenceKey> {
    List<TechnicalAnalysisEvidence> findByKeySelectionIdAndKeyStage(UUID selectionId, String stage);
}
