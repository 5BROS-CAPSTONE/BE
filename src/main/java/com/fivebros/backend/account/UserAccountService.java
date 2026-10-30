package com.fivebros.backend.account;

import java.time.Clock;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fivebros.backend.account.dto.CreateUserRequest;
import com.fivebros.backend.account.dto.UpdateUserRequest;
import com.fivebros.backend.account.dto.UserResponse;
import com.fivebros.backend.auth.AuthSessionRepository;
import com.fivebros.backend.common.PageResponse;
import com.fivebros.backend.common.error.ApiException;
import com.fivebros.backend.security.Role;

@Service
@Transactional(readOnly = true)
public class UserAccountService {

    private final UserAccountRepository users;
    private final AuthSessionRepository sessions;
    private final PasswordEncoder passwords;
    private final Clock clock;

    public UserAccountService(
            UserAccountRepository users, AuthSessionRepository sessions, PasswordEncoder passwords, Clock clock) {
        this.users = users;
        this.sessions = sessions;
        this.passwords = passwords;
        this.clock = clock;
    }

    public PageResponse<UserResponse> list(String search, AccountStatus status, int page, int size) {
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id"));
        return PageResponse.from(
                users.search(search.strip().toLowerCase(Locale.ROOT), status, PageRequest.of(page, size, sort))
                        .map(UserResponse::from));
    }

    public UserResponse get(UUID id) {
        return UserResponse.from(findUser(id));
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        String email = EmailAddress.normalize(request.email());
        if (users.existsByEmail(email)) {
            throw emailAlreadyExists();
        }
        UserAccount user = new UserAccount(
                email,
                request.fullName().strip(),
                passwords.encode(request.password()),
                request.roles(),
                clock.instant());
        return UserResponse.from(users.saveAndFlush(user));
    }

    @Transactional
    public UserResponse update(UUID actorId, UUID id, UpdateUserRequest request) {
        // Status and role changes share the same lock order to protect the last active admin.
        users.lockUsersWithRole(Role.ADMIN);
        requireAdministrator(actorId);
        UserAccount user = users.findByIdForUpdate(id).orElseThrow(this::userNotFound);
        String email = EmailAddress.normalize(request.email());
        if (users.existsByEmailAndIdNot(email, id)) {
            throw emailAlreadyExists();
        }
        user.updateProfile(email, request.fullName().strip(), clock.instant());
        if (request.status() != null && request.status() != user.getStatus()) {
            if (request.status() == AccountStatus.DISABLED
                    && user.getRoles().contains(Role.ADMIN)
                    && users.countActiveUsersWithRole(Role.ADMIN) <= 1) {
                throw lastAdministratorRequired();
            }
            if (request.status() == AccountStatus.ACTIVE && user.getRoles().isEmpty()) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "ACCOUNT_ROLES_REQUIRED",
                        "Assign at least one role before activating the account");
            }
            user.changeStatus(request.status(), clock.instant());
            if (!user.isActive()) {
                sessions.revokeAllForUser(id, clock.instant());
            }
        }
        users.flush();
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse resetPassword(UUID id, String password) {
        UserAccount user = users.findByIdForUpdate(id).orElseThrow(this::userNotFound);
        user.changePassword(passwords.encode(password), clock.instant());
        sessions.revokeAllForUser(id, clock.instant());
        users.flush();
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse assignRoles(UUID actorId, UUID userId, Set<Role> roles) {
        // Serialize role changes through the current admins to protect the last admin.
        users.lockUsersWithRole(Role.ADMIN);
        requireAdministrator(actorId);
        UserAccount user = users.findByIdForUpdate(userId).orElseThrow(this::userNotFound);
        if (user.isActive()
                && user.getRoles().contains(Role.ADMIN)
                && !roles.contains(Role.ADMIN)
                && users.countActiveUsersWithRole(Role.ADMIN) <= 1) {
            throw lastAdministratorRequired();
        }
        user.replaceRoles(roles, clock.instant());
        users.flush();
        return UserResponse.from(user);
    }

    private UserAccount findUser(UUID id) {
        return users.findById(id).orElseThrow(this::userNotFound);
    }

    private void requireAdministrator(UUID actorId) {
        UserAccount actor = findUser(actorId);
        if (!actor.isActive() || !actor.getRoles().contains(Role.ADMIN)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Administrator role is required");
        }
    }

    private ApiException lastAdministratorRequired() {
        return new ApiException(
                HttpStatus.CONFLICT, "LAST_ADMIN_REQUIRED", "The system must retain at least one active administrator");
    }

    private ApiException userNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User account was not found");
    }

    private ApiException emailAlreadyExists() {
        return new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "Email is already in use");
    }
}
