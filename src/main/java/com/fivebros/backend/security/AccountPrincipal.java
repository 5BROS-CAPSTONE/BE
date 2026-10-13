package com.fivebros.backend.security;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public record AccountPrincipal(UUID id, String email, String passwordHash, Set<Role> roles) implements UserDetails {

    public AccountPrincipal {
        roles = Set.copyOf(roles);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return RoleAuthorities.from(roles);
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String toString() {
        return "AccountPrincipal[id=" + id + "]";
    }
}
