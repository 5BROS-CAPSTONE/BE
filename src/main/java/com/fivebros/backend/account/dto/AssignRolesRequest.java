package com.fivebros.backend.account.dto;

import java.util.Set;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fivebros.backend.security.Role;

/** Đúng một role mỗi tài khoản, theo BPMN S5 - Người dùng &amp; Phân quyền / Phân quyền. */
public record AssignRolesRequest(
        @NotNull @Size(min = 1, max = 1, message = "Tài khoản phải có đúng một vai trò")
        Set<@NotNull Role> roles) {}
