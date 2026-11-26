package org.predictiveedge.chart.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface FundamentalAnalysisEvidenceRepository extends JpaRepository<FundamentalAnalysisEvidence, EvidenceKey> {
    List<FundamentalAnalysisEvidence> findByKeySelectionIdAndKeyStage(UUID selectionId, String stage);
}
