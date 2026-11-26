package org.predictiveedge.chart.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface MacroAndEconomicEvidenceRepository extends JpaRepository<MacroAndEconomicEvidence, EvidenceKey> {
    List<MacroAndEconomicEvidence> findByKeySelectionIdAndKeyStage(UUID selectionId, String stage);
}
