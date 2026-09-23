package com.financetracker.auth;

import com.financetracker.auth.dto.AuthResponse;
import com.financetracker.auth.dto.LoginRequest;
import com.financetracker.auth.dto.RegisterRequest;
import com.financetracker.common.exception.ConflictException;
import com.financetracker.common.security.CurrentUserService;
import com.financetracker.common.security.JwtService;
import com.financetracker.user.User;
import com.financetracker.user.UserRepository;
import com.financetracker.user.dto.UserResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CurrentUserService currentUserService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            CurrentUserService currentUserService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Email is already registered");
        }
        User user = new User();
        user.setEmail(email);
        user.setName(request.name().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setTokenVersion(0);
        userRepository.save(user);
        return new AuthResponse(jwtService.generate(user), UserResponse.from(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(normalizeEmail(request.email()))
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }
        return new AuthResponse(jwtService.generate(user), UserResponse.from(user));
    }

    @Transactional
    public void logout() {
        User user = currentUserService.require();
        user.setTokenVersion(user.getTokenVersion() + 1);
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }
}
