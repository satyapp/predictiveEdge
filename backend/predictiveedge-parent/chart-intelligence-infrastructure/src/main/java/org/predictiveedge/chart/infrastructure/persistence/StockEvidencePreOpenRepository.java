package org.predictiveedge.chart.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface StockEvidencePreOpenRepository extends JpaRepository<StockEvidencePreOpen, EvidenceKey> {
    List<StockEvidencePreOpen> findByKeySelectionIdAndKeyStage(UUID selectionId, String stage);
}
