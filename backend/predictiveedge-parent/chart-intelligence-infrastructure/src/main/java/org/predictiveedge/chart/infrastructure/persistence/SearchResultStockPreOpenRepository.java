package org.predictiveedge.chart.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface SearchResultStockPreOpenRepository extends JpaRepository<SearchResultStockPreOpen, SearchStockKey> { }
