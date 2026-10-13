package com.fivebros.backend.account.dto;

import java.util.Set;

import com.fivebros.backend.security.Role;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record AssignRolesRequest(@NotEmpty Set<@NotNull Role> roles) {
}
