package org.predictiveedge.chart.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface SearchResultPreOpenRepository extends JpaRepository<SearchResultPreOpen, SearchStockKey> { }
