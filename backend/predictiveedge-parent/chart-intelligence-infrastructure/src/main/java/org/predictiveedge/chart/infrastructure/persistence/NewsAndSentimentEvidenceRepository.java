package org.predictiveedge.chart.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface NewsAndSentimentEvidenceRepository extends JpaRepository<NewsAndSentimentEvidence, EvidenceKey> {
    List<NewsAndSentimentEvidence> findByKeySelectionIdAndKeyStage(UUID selectionId, String stage);
}
