package com.fivebros.backend.fleet;

import java.net.URI;
import java.time.Clock;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fivebros.backend.common.PageResponse;
import com.fivebros.backend.common.error.ApiException;

@Service
@Transactional(readOnly = true)
public class FleetService {
    private final UavProfileRepository profiles;
    private final DroneRepository drones;
    private final Clock clock;

    public FleetService(UavProfileRepository profiles, DroneRepository drones, Clock clock) {
        this.profiles = profiles;
        this.drones = drones;
        this.clock = clock;
    }

    public PageResponse<UavProfileResponse> listProfiles(String search, int page, int size) {
        String term = search.strip().toLowerCase(Locale.ROOT);
        Specification<UavProfile> filter = (root, query, cb) -> cb.or(
                cb.gt(cb.locate(cb.lower(root.get("manufacturer")), term), 0),
                cb.gt(cb.locate(cb.lower(root.get("modelName")), term), 0));
        return PageResponse.from(profiles.findAll(filter, paging(page, size)).map(UavProfileResponse::from));
    }

    public UavProfileResponse getProfile(UUID id) {
        return UavProfileResponse.from(findProfile(id));
    }

    @Transactional
    public UavProfileResponse createProfile(UUID actor, UavProfileRequest request) {
        validateProfile(request);
        UavProfile profile = new UavProfile(actor, clock.instant(), request);
        uniqueProfile(profile.identityKey, profile.id);
        return UavProfileResponse.from(profiles.saveAndFlush(profile));
    }

    @Transactional
    public UavProfileResponse updateProfile(UUID actor, UUID id, UavProfileRequest request) {
        UavProfile profile = findProfile(id);
        checkVersion(profile, request.version());
        validateProfile(request);
        uniqueProfile(UavProfile.identityKey(request.manufacturer(), request.modelName()), id);
        profile.update(request, actor, clock.instant());
        profiles.flush();
        return UavProfileResponse.from(profile);
    }

    public PageResponse<DroneResponse> listDrones(
            String search, DroneStatus status, UUID profileId, int page, int size) {
        String term = search.strip().toLowerCase(Locale.ROOT);
        Specification<Drone> filter = (root, query, cb) -> {
            var predicate = cb.or(
                    cb.gt(cb.locate(cb.lower(root.get("name")), term), 0),
                    cb.gt(cb.locate(cb.lower(root.get("serial")), term), 0));
            if (status != null) predicate = cb.and(predicate, cb.equal(root.get("status"), status));
            if (profileId != null)
                predicate = cb.and(predicate, cb.equal(root.get("uavProfile").get("id"), profileId));
            return predicate;
        };
        return PageResponse.from(drones.findAll(filter, paging(page, size)).map(DroneResponse::from));
    }

    public DroneResponse getDrone(UUID id) {
        return DroneResponse.from(findDrone(id));
    }

    @Transactional
    public DroneResponse createDrone(UUID actor, DroneRequest request) {
        UavProfile profile = findProfile(request.uavProfileId());
        Drone drone = new Drone(actor, clock.instant(), request, profile);
        uniqueSerial(drone.serialKey, drone.id);
        return DroneResponse.from(drones.saveAndFlush(drone));
    }

    @Transactional
    public DroneResponse updateDrone(UUID actor, UUID id, DroneRequest request) {
        Drone drone = findDrone(id);
        checkVersion(drone, request.version());
        if (drone.status == DroneStatus.IN_MISSION || drone.status == DroneStatus.RETIRED) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "DRONE_NOT_EDITABLE", "An in-mission or retired drone cannot be edited");
        }
        UavProfile profile = findProfile(request.uavProfileId());
        uniqueSerial(request.serial().strip().toLowerCase(Locale.ROOT), id);
        drone.update(request, profile, actor, clock.instant());
        drones.flush();
        return DroneResponse.from(drone);
    }

    private void validateProfile(UavProfileRequest request) {
        ProfileSpecifications specs = request.specifications();
        if (specs.minOperatingTemperatureC() != null
                && specs.maxOperatingTemperatureC() != null
                && specs.minOperatingTemperatureC().compareTo(specs.maxOperatingTemperatureC()) > 0) {
            throw badRequest("INVALID_TEMPERATURE_RANGE", "Minimum operating temperature must not exceed maximum");
        }
        if (specs.maxTakeoffWeightKg() != null) {
            if ((specs.emptyWeightKg() != null && specs.emptyWeightKg().compareTo(specs.maxTakeoffWeightKg()) > 0)
                    || (specs.maxPayloadKg() != null
                            && specs.maxPayloadKg().compareTo(specs.maxTakeoffWeightKg()) > 0)) {
                throw badRequest("INVALID_WEIGHT_LIMITS", "Empty weight and payload must not exceed MTOW individually");
            }
        }
        String sourceUrl = UavProfile.clean(request.sourceUrl());
        if (sourceUrl != null) {
            try {
                URI uri = URI.create(sourceUrl);
                if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                        || uri.getHost() == null
                        || uri.getUserInfo() != null) {
                    throw new IllegalArgumentException();
                }
            } catch (IllegalArgumentException exception) {
                throw badRequest("INVALID_SOURCE_URL", "sourceUrl must be an absolute HTTP(S) URL without credentials");
            }
        }
        long distinct = request.capabilities().stream()
                .map(value -> value.toUpperCase(Locale.ROOT))
                .distinct()
                .count();
        if (distinct != request.capabilities().size()) {
            throw badRequest("DUPLICATE_CAPABILITY", "Capability codes must be unique ignoring case");
        }
    }

    private void uniqueProfile(String key, UUID id) {
        if (profiles.existsByIdentityKeyAndIdNot(key, id)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "UAV_PROFILE_EXISTS",
                    "A profile with this manufacturer and model already exists");
        }
    }

    private void uniqueSerial(String key, UUID id) {
        if (drones.existsBySerialKeyAndIdNot(key, id)) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "DRONE_SERIAL_EXISTS", "A drone with this serial already exists");
        }
    }

    private UavProfile findProfile(UUID id) {
        return profiles.findById(id)
                .orElseThrow(() ->
                        new ApiException(HttpStatus.NOT_FOUND, "UAV_PROFILE_NOT_FOUND", "UAV Profile was not found"));
    }

    private Drone findDrone(UUID id) {
        return drones.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DRONE_NOT_FOUND", "Drone was not found"));
    }

    private void checkVersion(FleetRecord record, Long version) {
        if (version == null) throw badRequest("VERSION_REQUIRED", "Supply the latest version when updating");
        if (!version.equals(record.version)) {
            throw new ApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT", "Data has changed; reload before updating");
        }
    }

    private PageRequest paging(int page, int size) {
        return PageRequest.of(
                page, size, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id")));
    }

    private ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }
}
