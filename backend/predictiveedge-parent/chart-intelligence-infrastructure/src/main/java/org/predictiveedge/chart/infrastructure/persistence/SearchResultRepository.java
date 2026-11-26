package org.predictiveedge.chart.infrastructure.persistence;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SearchResultRepository extends JpaRepository<SearchResult, UUID> {
    Optional<SearchResult> findFirstByUserIdAndCategoryAndTradingDateOrderBySearchedAtDesc(
            UUID userId, String category, LocalDate tradingDate);
}
