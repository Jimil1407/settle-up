package com.settleup.auth;

import com.settleup.auth.dto.AuthResponse;
import com.settleup.auth.dto.LoginRequest;
import com.settleup.auth.dto.RegisterRequest;
import com.settleup.common.ValidationException;
import com.settleup.user.User;
import com.settleup.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ValidationException("an account with that email already exists");
        }
        User user = userRepository.save(User.builder()
                .email(email)
                .displayName(request.displayName().trim())
                .passwordHash(passwordEncoder.encode(request.password()))
                .createdAt(Instant.now())
                .build());
        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email().trim())
                // Deliberately identical message for "no such user" and "wrong password" so the
                // endpoint cannot be used to enumerate which emails are registered.
                .orElseThrow(() -> new ValidationException("invalid email or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ValidationException("invalid email or password");
        }
        return toResponse(user);
    }

    private AuthResponse toResponse(User user) {
        return new AuthResponse(
                jwtService.issue(user.getId(), user.getEmail()),
                user.getId(),
                user.getEmail(),
                user.getDisplayName());
    }
}
