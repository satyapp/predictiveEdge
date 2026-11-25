package org.predictiveedge.chart.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface SearchResultStockMostActiveByValueRepository extends JpaRepository<SearchResultStockMostActiveByValue, SearchStockKey> { }
