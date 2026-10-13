package com.fivebros.backend.auth;

import java.util.UUID;

import com.fivebros.backend.account.UserAccountService;
import com.fivebros.backend.account.dto.UserResponse;
import com.fivebros.backend.auth.dto.LoginRequest;
import com.fivebros.backend.auth.dto.LoginResponse;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authentication;
    private final UserAccountService users;

    public AuthController(AuthService authentication, UserAccountService users) {
        this.authentication = authentication;
        this.users = users;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(authentication.login(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal Jwt jwt) {
        authentication.logout(UUID.fromString(jwt.getSubject()), UUID.fromString(jwt.getId()));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return users.get(UUID.fromString(jwt.getSubject()));
    }
}
