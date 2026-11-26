package org.predictiveedge.chart.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ChartPatternsEvidenceRepository extends JpaRepository<ChartPatternsEvidence, EvidenceKey> {
    List<ChartPatternsEvidence> findByKeySelectionIdAndKeyStage(UUID selectionId, String stage);
}
