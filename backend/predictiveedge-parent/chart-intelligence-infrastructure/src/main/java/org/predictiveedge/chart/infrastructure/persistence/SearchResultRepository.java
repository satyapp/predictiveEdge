package org.predictiveedge.chart.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SearchResultRepository extends JpaRepository<SearchResult, UUID> { }
