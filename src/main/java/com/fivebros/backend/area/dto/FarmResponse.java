package com.fivebros.backend.area.dto;

import java.time.Instant;
import java.util.UUID;
import com.fivebros.backend.area.Farm;

public record FarmResponse(UUID id, String name, String description,
                           UUID createdBy, UUID updatedBy, Instant createdAt, Instant updatedAt,
                           long version, Long fieldCount) {
    public static FarmResponse from(Farm farm, long fieldCount) {
        return new FarmResponse(farm.getId(), farm.getName(), farm.getDescription(), farm.getCreatedBy(), farm.getUpdatedBy(),
                farm.getCreatedAt(), farm.getUpdatedAt(), farm.getVersion(), fieldCount);
    }
}
