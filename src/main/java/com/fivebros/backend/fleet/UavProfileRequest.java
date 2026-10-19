package com.fivebros.backend.fleet;

import java.time.LocalDate;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;

public record UavProfileRequest(
        @NotBlank @Size(max = 100) @Pattern(regexp = "[^\\p{Cntrl}]+") String manufacturer,
        @NotBlank @Size(max = 160) @Pattern(regexp = "[^\\p{Cntrl}]+") String modelName,
        @NotNull @Valid ProfileSpecifications specifications,
        @NotNull @Size(max = 100) List<@NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z][A-Za-z0-9_:-]*") String> capabilities,
        @Size(max = 2000) String sourceUrl,
        LocalDate sourceAccessedOn,
        @Size(max = 160) String sourceRecordedBy,
        @Schema(description = "Defaults to UNVERIFIED; does not imply datasheet verification")
        ProfileDataStatus dataStatus,
        @Schema(description = "Datasheet references, missing data, operating mode and measurement conditions")
        @Size(max = 8000) String notes,
        @Schema(description = "Required for PUT; omit for POST")
        @PositiveOrZero Long version
) { }

