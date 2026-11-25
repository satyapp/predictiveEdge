package org.predictiveedge.chart.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface SelectedStockAiAnalysisRepository extends JpaRepository<SelectedStockAiAnalysis, AiAnalysisKey> { }
