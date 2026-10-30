package com.fivebros.backend.area;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FieldRepository extends JpaRepository<Field, UUID> {
    Page<Field> findByFarmIdAndNameContainingIgnoreCase(UUID farmId, String search, Pageable pageable);

    long countByFarmId(UUID farmId);
}
