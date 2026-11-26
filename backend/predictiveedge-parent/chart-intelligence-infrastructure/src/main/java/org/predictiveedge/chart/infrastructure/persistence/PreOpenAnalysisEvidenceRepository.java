package org.predictiveedge.chart.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PreOpenAnalysisEvidenceRepository extends JpaRepository<PreOpenAnalysisEvidence, EvidenceKey> {
    List<PreOpenAnalysisEvidence> findByKeySelectionIdAndKeyStage(UUID selectionId, String stage);
}
