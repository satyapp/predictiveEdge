package org.predictiveedge.chart.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface HistoricalAndPredictiveEvidenceRepository extends JpaRepository<HistoricalAndPredictiveEvidence, EvidenceKey> { }
