package com.fivebros.backend.fleet;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.*;

@MappedSuperclass
public abstract class FleetRecord {
    @Id
    UUID id;

    @Column(nullable = false, updatable = false)
    UUID createdBy;

    @Column(nullable = false)
    UUID updatedBy;

    @Column(nullable = false, updatable = false)
    Instant createdAt;

    @Column(nullable = false)
    Instant updatedAt;

    @Version
    Long version;

    protected FleetRecord() {}

    protected FleetRecord(UUID actor, Instant now) {
        id = UUID.randomUUID();
        createdBy = actor;
        createdAt = now;
        touch(actor, now);
    }

    void touch(UUID actor, Instant now) {
        updatedBy = actor;
        updatedAt = now;
    }
}
