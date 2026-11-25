package org.predictiveedge.chart.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface SearchResultStockNearBreakoutsRepository extends JpaRepository<SearchResultStockNearBreakouts, SearchStockKey> { }
