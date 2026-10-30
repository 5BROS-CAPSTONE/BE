package com.fivebros.backend.area;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "farms")
public class Farm extends AreaRecord {
    @Column(nullable = false, length = 160)
    private String name;

    @Column(length = 2000)
    private String description;

    protected Farm() {}

    Farm(UUID actor, Instant now, String name, String description) {
        super(actor, now);
        update(name, description, actor, now);
    }

    void update(String name, String description, UUID actor, Instant now) {
        this.name = name.strip();
        this.description = description == null ? null : description.strip();
        touch(actor, now);
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }
}
