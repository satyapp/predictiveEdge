package org.predictiveedge.chart.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface MarketDataEvidenceRepository extends JpaRepository<MarketDataEvidence, EvidenceKey> { }
