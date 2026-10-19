package com.fivebros.backend.fleet;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record UavProfileResponse(
        UUID id, String manufacturer, String modelName, ProfileSpecifications specifications,
        List<String> capabilities, String sourceUrl, LocalDate sourceAccessedOn, String sourceRecordedBy,
        ProfileDataStatus dataStatus, String notes,
        UUID createdBy, UUID updatedBy, Instant createdAt, Instant updatedAt, long version
) {
    static UavProfileResponse from(UavProfile profile) {
        // Drone.profile may be a lazy proxy; read fields from the initialized implementation.
        profile = org.hibernate.Hibernate.unproxy(profile, UavProfile.class);
        return new UavProfileResponse(profile.id, profile.manufacturer, profile.modelName, profile.specifications(),
                profile.capabilities.stream().sorted().toList(), profile.sourceUrl, profile.sourceAccessedOn,
                profile.sourceRecordedBy, profile.dataStatus, profile.notes,
                profile.createdBy, profile.updatedBy, profile.createdAt, profile.updatedAt, profile.version);
    }
}

