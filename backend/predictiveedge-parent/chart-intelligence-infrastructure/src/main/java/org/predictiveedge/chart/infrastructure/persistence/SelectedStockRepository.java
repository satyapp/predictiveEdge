package org.predictiveedge.chart.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SelectedStockRepository extends JpaRepository<SelectedStock, UUID> {
    boolean existsBySearchIdAndSymbol(UUID searchId, String symbol);
}
