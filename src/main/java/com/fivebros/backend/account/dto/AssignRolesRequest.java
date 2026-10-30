package com.fivebros.backend.account.dto;

import java.util.Set;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import com.fivebros.backend.security.Role;

public record AssignRolesRequest(@NotEmpty Set<@NotNull Role> roles) {}
