package com.fivebros.backend.area;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;

@MappedSuperclass
public abstract class AreaRecord {
    @Id
    private UUID id;
    @Column(nullable = false, updatable = false)
    private UUID createdBy;
    @Column(nullable = false)
    private UUID updatedBy;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected AreaRecord() { }

    protected AreaRecord(UUID actor, Instant now) {
        id = UUID.randomUUID();
        createdBy = actor;
        updatedBy = actor;
        createdAt = now;
        updatedAt = now;
    }

    void touch(UUID actor, Instant now) {
        updatedBy = actor;
        updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getCreatedBy() { return createdBy; }
    public UUID getUpdatedBy() { return updatedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
