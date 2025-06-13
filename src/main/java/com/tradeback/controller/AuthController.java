package com.tradeback.controller;

import com.tradeback.config.JwtService;
import com.tradeback.dto.LoginRequest;
import com.tradeback.dto.RegistrationRequest;
import com.tradeback.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegistrationRequest request,
                                      BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            String errors = bindingResult.getFieldErrors().stream()
                    .map(error -> error.getField() + ": " + error.getDefaultMessage())
                    .collect(Collectors.joining(", "));
            return ResponseEntity.badRequest().body(Map.of("error", errors));
        }

        try {
            userService.register(request.getUsername(), request.getPassword(), request.getEmail());
            log.info("User registered successfully: {}", request.getUsername());
            return ResponseEntity.ok(Map.of("message", "User registered successfully"));
        } catch (RuntimeException e) {
            log.warn("Registration failed for user {}: {}", request.getUsername(), e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request,
                                   BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            String errors = bindingResult.getFieldErrors().stream()
                    .map(error -> error.getField() + ": " + error.getDefaultMessage())
                    .collect(Collectors.joining(", "));
            return ResponseEntity.badRequest().body(Map.of("error", errors));
        }

        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );

            User user = (User) auth.getPrincipal();
            String token = jwtService.generateToken(user);

            log.info("User logged in successfully: {}", request.getUsername());
            return ResponseEntity.ok(Map.of("token", token, "username", user.getUsername()));

        } catch (BadCredentialsException e) {
            log.warn("Login failed for user {}: Invalid credentials", request.getUsername());
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid username or password"));
        } catch (Exception e) {
            log.error("Login error for user {}: {}", request.getUsername(), e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", "Authentication failed"));
        }
    }
}