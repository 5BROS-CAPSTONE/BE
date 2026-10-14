package com.fivebros.backend.account;

import java.time.Clock;
import java.util.Set;
import java.util.UUID;

import com.fivebros.backend.account.dto.RegistrationResponse;
import com.fivebros.backend.account.dto.RegisterUserRequest;
import com.fivebros.backend.account.dto.UserResponse;
import com.fivebros.backend.auth.AuthSessionRepository;
import com.fivebros.backend.common.error.ApiException;
import com.fivebros.backend.security.Role;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RegistrationService {

    private final UserAccountRepository users;
    private final AuthSessionRepository sessions;
    private final PasswordEncoder passwords;
    private final Clock clock;

    public RegistrationService(UserAccountRepository users, AuthSessionRepository sessions,
                               PasswordEncoder passwords, Clock clock) {
        this.users = users;
        this.sessions = sessions;
        this.passwords = passwords;
        this.clock = clock;
    }

    public RegistrationResponse register(RegisterUserRequest request) {
        if (request.requestedRole() == Role.ADMIN) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUESTED_ROLE",
                    "Administrator cannot be requested during registration");
        }
        String email = EmailAddress.normalize(request.email());
        if (users.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "Email is already in use");
        }
        UserAccount user = UserAccount.pendingRegistration(email, request.fullName().strip(),
                passwords.encode(request.password()), request.requestedRole(), clock.instant());
        return RegistrationResponse.from(users.saveAndFlush(user));
    }

    public UserResponse approve(UUID reviewerId, UUID userId, Set<Role> roles) {
        if (roles.contains(Role.ADMIN)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REGISTRATION_ROLES",
                    "Registration approval may only grant non-administrator roles");
        }
        UserAccount user = pendingRegistrationForReview(reviewerId, userId);
        user.approveRegistration(roles, reviewerId, clock.instant());
        users.flush();
        return UserResponse.from(user);
    }

    public UserResponse reject(UUID reviewerId, UUID userId, String reason) {
        UserAccount user = pendingRegistrationForReview(reviewerId, userId);
        user.rejectRegistration(reason.strip(), reviewerId, clock.instant());
        sessions.revokeAllForUser(userId, clock.instant());
        users.flush();
        return UserResponse.from(user);
    }

    private UserAccount pendingRegistrationForReview(UUID reviewerId, UUID userId) {
        // Use the same lock order as role changes so a concurrent demotion cannot approve an account.
        users.lockUsersWithRole(Role.ADMIN);
        UserAccount reviewer = users.findById(reviewerId).orElseThrow(this::userNotFound);
        if (!reviewer.isActive() || !reviewer.getRoles().contains(Role.ADMIN)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Administrator role is required");
        }
        UserAccount user = users.findByIdForUpdate(userId).orElseThrow(this::userNotFound);
        if (user.getStatus() != AccountStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "REGISTRATION_ALREADY_REVIEWED",
                    "Only pending registrations can be reviewed");
        }
        return user;
    }

    private ApiException userNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User account was not found");
    }
}
