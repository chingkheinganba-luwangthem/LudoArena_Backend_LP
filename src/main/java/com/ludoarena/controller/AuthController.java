package com.ludoarena.controller;

import com.ludoarena.dto.*;
import com.ludoarena.model.User;
import com.ludoarena.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication Controller - REST endpoints for user authentication.
 *
 * THREAD NOTE:
 * - Each request to these endpoints is handled by a separate Tomcat thread
 *   from the thread pool (default 200 threads).
 * - Multiple signup/login requests are processed concurrently.
 * - The @Valid annotation triggers Bean Validation on the request thread
 *   before the method body executes.
 *
 * Endpoints:
 *   POST /api/auth/signup    → Register new user (public)
 *   POST /api/auth/login     → Login with email/password (public)
 *   POST /api/auth/guest     → Guest login (public)
 *   GET  /api/auth/me        → Get current user profile (protected - needs JWT)
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * POST /api/auth/signup
     * Register a new user with name, email, and password.
     *
     * Request Body: { "name": "John", "email": "john@mail.com", "password": "pass123" }
     * Response: { "token": "eyJ...", "tokenType": "Bearer", "userId": 1, ... }
     *
     * THREAD: Runs on a Tomcat HTTP thread. @Valid triggers validation on the same thread.
     */
    @PostMapping("/signup")
    public ResponseEntity<?> signup(@Valid @RequestBody SignupRequest request) {
        try {
            AuthResponse response = authService.signup(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("User registered successfully!", response));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * POST /api/auth/login
     * Authenticate user with email and password.
     *
     * Request Body: { "email": "john@mail.com", "password": "pass123" }
     * Response: { "token": "eyJ...", "tokenType": "Bearer", ... }
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            AuthResponse response = authService.login(request);
            return ResponseEntity.ok(ApiResponse.success("Login successful!", response));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * POST /api/auth/guest
     * Create a guest account with limited features.
     *
     * No request body needed.
     * Response: { "token": "eyJ...", "userId": 5, "name": "Guest_abc123", "isGuest": true }
     */
    @PostMapping("/guest")
    public ResponseEntity<?> guestLogin() {
        AuthResponse response = authService.guestLogin();
        return ResponseEntity.ok(ApiResponse.success("Guest login successful!", response));
    }

    /**
     * POST /api/auth/google
     * Authenticate or register using a Google ID Token.
     */
    @PostMapping("/google")
    public ResponseEntity<?> googleLogin(@Valid @RequestBody GoogleLoginRequest request) {
        try {
            AuthResponse response = authService.googleLogin(request);
            return ResponseEntity.ok(ApiResponse.success("Google login successful!", response));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * GET /api/auth/me
     * Get the currently authenticated user's profile.
     * Requires JWT token in Authorization header.
     *
     * Headers: Authorization: Bearer eyJ...
     * Response: { "id": 1, "name": "John", "coins": 1000, ... }
     *
     * THREAD: @AuthenticationPrincipal extracts User from ThreadLocal SecurityContext
     * set by JwtAuthFilter on the same thread.
     */
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Not authenticated"));
        }
        UserDTO profile = authService.getUserProfile(user.getId());
        return ResponseEntity.ok(ApiResponse.success("Profile fetched!", profile));
    }

    /**
     * PUT /api/auth/profile
     * Update the currently authenticated user's profile.
     */
    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@AuthenticationPrincipal User user, @Valid @RequestBody UpdateProfileRequest request) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Not authenticated"));
        }
        try {
            UserDTO updatedProfile = authService.updateProfile(user.getId(), request);
            return ResponseEntity.ok(ApiResponse.success("Profile updated!", updatedProfile));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}
