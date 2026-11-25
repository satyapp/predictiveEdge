package org.predictiveedge.chart.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface ChartPatternsEvidenceRepository extends JpaRepository<ChartPatternsEvidence, EvidenceKey> { }
