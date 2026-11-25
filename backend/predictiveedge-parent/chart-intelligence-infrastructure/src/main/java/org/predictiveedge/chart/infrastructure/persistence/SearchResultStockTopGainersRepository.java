package org.predictiveedge.chart.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface SearchResultStockTopGainersRepository extends JpaRepository<SearchResultStockTopGainers, SearchStockKey> { }
