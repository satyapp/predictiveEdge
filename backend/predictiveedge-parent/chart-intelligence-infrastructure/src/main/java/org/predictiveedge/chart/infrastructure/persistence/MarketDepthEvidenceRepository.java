package org.predictiveedge.chart.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface MarketDepthEvidenceRepository extends JpaRepository<MarketDepthEvidence, EvidenceKey> {
    List<MarketDepthEvidence> findByKeySelectionIdAndKeyStage(UUID selectionId, String stage);
}
