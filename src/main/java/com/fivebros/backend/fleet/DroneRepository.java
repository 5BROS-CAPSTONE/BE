package com.fivebros.backend.fleet;

import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface DroneRepository extends JpaRepository<Drone, UUID>, JpaSpecificationExecutor<Drone> {
    @Override
    @EntityGraph(attributePaths = "uavProfile")
    Optional<Drone> findById(UUID id);

    @Override
    @EntityGraph(attributePaths = "uavProfile")
    Page<Drone> findAll(Specification<Drone> specification, Pageable pageable);

    boolean existsBySerialKeyAndIdNot(String serialKey, UUID id);
}

