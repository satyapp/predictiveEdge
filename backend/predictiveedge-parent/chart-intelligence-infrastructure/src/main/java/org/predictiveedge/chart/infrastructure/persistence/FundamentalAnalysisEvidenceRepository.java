package org.predictiveedge.chart.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface FundamentalAnalysisEvidenceRepository extends JpaRepository<FundamentalAnalysisEvidence, EvidenceKey> { }
