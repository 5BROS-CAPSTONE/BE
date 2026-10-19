package com.fivebros.backend.fleet;

/** Read-only in FLT-01/02/03; transitions belong to fleet lifecycle and mission services. */
public enum DroneStatus { AVAILABLE, IN_MISSION, UNAVAILABLE, RETIRED }

