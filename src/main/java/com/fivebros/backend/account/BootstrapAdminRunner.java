package com.fivebros.backend.account;

import java.time.Clock;
import java.util.Set;

import jakarta.validation.Validator;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fivebros.backend.account.dto.CreateUserRequest;
import com.fivebros.backend.config.BootstrapAdminProperties;
import com.fivebros.backend.security.Role;

@Component
public class BootstrapAdminRunner implements ApplicationRunner {

    private final BootstrapAdminProperties properties;
    private final UserAccountRepository users;
    private final PasswordEncoder passwords;
    private final Validator validator;
    private final Clock clock;

    public BootstrapAdminRunner(
            BootstrapAdminProperties properties,
            UserAccountRepository users,
            PasswordEncoder passwords,
            Validator validator,
            Clock clock) {
        this.properties = properties;
        this.users = users;
        this.passwords = passwords;
        this.validator = validator;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            return;
        }
        CreateUserRequest request = new CreateUserRequest(
                properties.email(), properties.fullName(), properties.password(), Set.of(Role.ADMIN));
        if (!validator.validate(request).isEmpty()) {
            throw new IllegalStateException("Bootstrap admin email, name or password is invalid");
        }
        String email = EmailAddress.normalize(request.email());
        var existing = users.findByEmail(email);
        if (existing.isPresent()) {
            if (!existing.get().getRoles().contains(Role.ADMIN)) {
                throw new IllegalStateException("Bootstrap email belongs to an existing non-admin account");
            }
            return;
        }
        users.saveAndFlush(new UserAccount(
                email,
                request.fullName().strip(),
                passwords.encode(request.password()),
                request.roles(),
                clock.instant()));
    }
}
