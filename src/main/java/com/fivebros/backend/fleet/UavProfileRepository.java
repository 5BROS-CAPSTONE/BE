package com.fivebros.backend.fleet;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface UavProfileRepository extends JpaRepository<UavProfile, UUID>, JpaSpecificationExecutor<UavProfile> {
    boolean existsByIdentityKeyAndIdNot(String identityKey, UUID id);
}
