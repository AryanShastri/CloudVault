package com.cloudvault.storage_engine.service;

import com.cloudvault.storage_engine.dto.AuthDtos.*;
import com.cloudvault.storage_engine.entity.User;
import com.cloudvault.storage_engine.enums.UserRole;
import com.cloudvault.storage_engine.exception.ConflictException;
import com.cloudvault.storage_engine.repository.UserRepository;
import com.cloudvault.storage_engine.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ConflictException("Username already taken: "
                    + request.getUsername());
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email already registered: "
                    + request.getEmail());
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(UserRole.USER)
                .tenantId(UUID.randomUUID().toString())
                .active(true)
                .build();

        user = userRepository.save(user);

        UserDetails userDetails =
                userDetailsService.loadUserByUsername(user.getUsername());
        String token = jwtUtils.generateToken(
                userDetails, user.getId(), user.getTenantId());

        return new AuthResponse(token, user.getUsername(),
                user.getEmail(), user.getTenantId(), user.getRole().name());
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(), request.getPassword()));

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        UserDetails userDetails =
                userDetailsService.loadUserByUsername(user.getUsername());
        String token = jwtUtils.generateToken(
                userDetails, user.getId(), user.getTenantId());

        return new AuthResponse(token, user.getUsername(),
                user.getEmail(), user.getTenantId(), user.getRole().name());
    }
}