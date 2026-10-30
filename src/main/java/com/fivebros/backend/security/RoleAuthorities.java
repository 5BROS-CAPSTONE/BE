package com.fivebros.backend.security;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public final class RoleAuthorities {

    private RoleAuthorities() {}

    public static Collection<GrantedAuthority> from(Set<Role> roles) {
        return roles.stream()
                .flatMap(role -> Stream.concat(
                        Stream.of("ROLE_" + role.name()),
                        role.permissions().stream().map(Permission::authority)))
                .distinct()
                .sorted()
                .map(authority -> (GrantedAuthority) new SimpleGrantedAuthority(authority))
                .toList();
    }
}
