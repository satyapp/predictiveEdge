package org.predictiveedge.chart.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface NewsAndSentimentEvidenceRepository extends JpaRepository<NewsAndSentimentEvidence, EvidenceKey> { }
