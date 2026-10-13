package com.fivebros.backend.security;

import com.fivebros.backend.account.UserAccountRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountDetailsService implements UserDetailsService {

    private final UserAccountRepository users;

    public AccountDetailsService(UserAccountRepository users) {
        this.users = users;
    }

    @Override
    @Transactional
    public UserDetails loadUserByUsername(String email) {
        // The login transaction holds this lock through password checking and session creation.
        var user = users.findByEmailForUpdate(email)
                .orElseThrow(() -> new UsernameNotFoundException("Account was not found"));
        return new AccountPrincipal(user.getId(), user.getEmail(), user.getPasswordHash(), user.getRoles());
    }
}
