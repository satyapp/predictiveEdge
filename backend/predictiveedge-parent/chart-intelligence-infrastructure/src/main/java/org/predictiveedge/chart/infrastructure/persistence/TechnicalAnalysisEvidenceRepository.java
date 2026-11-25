package org.predictiveedge.chart.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface TechnicalAnalysisEvidenceRepository extends JpaRepository<TechnicalAnalysisEvidence, EvidenceKey> { }
