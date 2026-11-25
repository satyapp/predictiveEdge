package org.predictiveedge.chart.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface IntradayListRepository extends JpaRepository<IntradayList, UUID> { }
