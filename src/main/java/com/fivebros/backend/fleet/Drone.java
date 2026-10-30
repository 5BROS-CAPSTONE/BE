package com.fivebros.backend.fleet;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import jakarta.persistence.*;

@Entity
@Table(name = "drones")
public class Drone extends FleetRecord {
    @Column(nullable = false, length = 100)
    String serial;

    @Column(nullable = false, unique = true, length = 100)
    String serialKey;

    @Column(nullable = false, length = 160)
    String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uav_profile_id", nullable = false)
    UavProfile uavProfile;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    DroneStatus status = DroneStatus.AVAILABLE;

    protected Drone() {}

    Drone(UUID actor, Instant now, DroneRequest request, UavProfile profile) {
        super(actor, now);
        update(request, profile, actor, now);
    }

    void update(DroneRequest request, UavProfile profile, UUID actor, Instant now) {
        serial = request.serial().strip();
        serialKey = serial.toLowerCase(Locale.ROOT);
        name = request.name().strip();
        uavProfile = profile;
        touch(actor, now);
    }
}
