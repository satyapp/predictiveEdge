package org.predictiveedge.chart.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface MarketDataEvidenceRepository extends JpaRepository<MarketDataEvidence, EvidenceKey> {
    List<MarketDataEvidence> findByKeySelectionIdAndKeyStage(UUID selectionId, String stage);
}
