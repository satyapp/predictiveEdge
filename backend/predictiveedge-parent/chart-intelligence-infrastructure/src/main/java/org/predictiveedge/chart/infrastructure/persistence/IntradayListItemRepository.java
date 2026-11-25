package org.predictiveedge.chart.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface IntradayListItemRepository extends JpaRepository<IntradayListItem, IntradayListItemKey> { }
