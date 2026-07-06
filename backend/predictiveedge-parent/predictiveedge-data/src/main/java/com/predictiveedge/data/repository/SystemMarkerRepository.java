package com.predictiveedge.data.repository;

import com.predictiveedge.data.entity.SystemMarker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SystemMarkerRepository extends JpaRepository<SystemMarker, Long> {
}
