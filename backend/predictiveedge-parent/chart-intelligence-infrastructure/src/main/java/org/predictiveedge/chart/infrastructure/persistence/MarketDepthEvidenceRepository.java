package org.predictiveedge.chart.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface MarketDepthEvidenceRepository extends JpaRepository<MarketDepthEvidence, EvidenceKey> { }
