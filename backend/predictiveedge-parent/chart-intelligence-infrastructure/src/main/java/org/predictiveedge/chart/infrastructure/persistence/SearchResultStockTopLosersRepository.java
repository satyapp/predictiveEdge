package org.predictiveedge.chart.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface SearchResultStockTopLosersRepository extends JpaRepository<SearchResultStockTopLosers, SearchStockKey> { }
