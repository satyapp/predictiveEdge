package org.predictiveedge.chart.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface CorporateActionsEvidenceRepository extends JpaRepository<CorporateActionsEvidence, EvidenceKey> { }
