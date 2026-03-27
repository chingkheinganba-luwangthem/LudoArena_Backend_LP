package com.ludoarena.service;

import com.ludoarena.dto.*;
import com.ludoarena.model.AuthProvider;
import com.ludoarena.model.User;
import com.ludoarena.repository.UserRepository;
import com.ludoarena.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

/**
 * Authentication Service - Handles signup, login, and guest authentication.
 *
 * THREAD SAFETY:
 * - This is a stateless Spring singleton service (no mutable instance fields).
 * - All injected dependencies (UserRepository, PasswordEncoder, JwtTokenProvider)
 *   are themselves thread-safe.
 * - Multiple Tomcat threads can call these methods concurrently without issues.
 * - @Transactional ensures each method runs within its own DB transaction,
 *   scoped to the calling thread's EntityManager.
 *
 * Handles multiple concurrent request scenarios:
 * - Two users signing up with the same email: The unique constraint on email
 *   in the DB will cause one to fail with a DataIntegrityViolationException.
 * - Concurrent login attempts: Each runs independently on its own thread.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String googleClientId;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
    }

    /**
     * Authenticate or register a user using a Google ID Token.
     */
    @Transactional
    public AuthResponse googleLogin(GoogleLoginRequest request) {
        try {
            NetHttpTransport transport = new NetHttpTransport();
            GsonFactory jsonFactory = GsonFactory.getDefaultInstance();

            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(transport, jsonFactory)
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();

            GoogleIdToken idToken = verifier.verify(request.getIdToken());
            if (idToken == null) {
                throw new RuntimeException("Invalid Google ID Token.");
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            String email = payload.getEmail();
            String name = (String) payload.get("name");
            String pictureUrl = (String) payload.get("picture");

            // Find user by email or create new
            User user = userRepository.findByEmail(email).orElseGet(() -> {
                User newUser = User.builder()
                        .name(name)
                        .email(email)
                        .avatarUrl(pictureUrl)
                        .authProvider(AuthProvider.GOOGLE)
                        .coins(1000L)
                        .isGuest(false)
                        .build();
                return userRepository.save(newUser);
            });

            // Generate JWT token
            String token = tokenProvider.generateToken(user.getId());

            return AuthResponse.of(token, user.getId(), user.getName(),
                    user.getEmail(), user.getAvatarUrl(), user.getCoins(), false);
        } catch (Exception e) {
            throw new RuntimeException("Google authentication failed: " + e.getMessage());
        }
    }

    /**
     * Register a new user with email and password.
     *
     * @param request SignupRequest with name, email, password
     * @return AuthResponse with JWT token and user info
     * @throws RuntimeException if email is already registered
     */
    @Transactional
    public AuthResponse signup(SignupRequest request) {
        // Check if email is already taken
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email is already registered. Please login instead.");
        }

        // Create new user with hashed password
        // THREAD: BCryptPasswordEncoder.encode() is thread-safe
        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .authProvider(AuthProvider.LOCAL)
                .coins(1000L)
                .isGuest(false)
                .build();

        user = userRepository.save(user);

        // Generate JWT token
        String token = tokenProvider.generateToken(user.getId());

        return AuthResponse.of(token, user.getId(), user.getName(),
                user.getEmail(), user.getAvatarUrl(), user.getCoins(), false);
    }

    /**
     * Authenticate user with email and password.
     *
     * @param request LoginRequest with email and password
     * @return AuthResponse with JWT token and user info
     * @throws RuntimeException if credentials are invalid
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        // Find user by email
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found with this email."));

        // Verify password
        // THREAD: BCryptPasswordEncoder.matches() is thread-safe
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid password.");
        }

        // Generate JWT token
        String token = tokenProvider.generateToken(user.getId());

        return AuthResponse.of(token, user.getId(), user.getName(),
                user.getEmail(), user.getAvatarUrl(), user.getCoins(), user.getIsGuest());
    }

    /**
     * Create a guest account with limited features.
     * Guest users get a random name and no email/password.
     *
     * @return AuthResponse with JWT token and guest user info
     */
    @Transactional
    public AuthResponse guestLogin() {
        // Generate a unique guest name
        String guestName = "Guest_" + UUID.randomUUID().toString().substring(0, 8);

        User guest = User.builder()
                .name(guestName)
                .authProvider(AuthProvider.GUEST)
                .coins(500L)  // Guests get fewer starting coins
                .isGuest(true)
                .build();

        guest = userRepository.save(guest);

        // Generate JWT token for guest
        String token = tokenProvider.generateToken(guest.getId());

        return AuthResponse.of(token, guest.getId(), guest.getName(),
                null, null, guest.getCoins(), true);
    }

    /**
     * Get user profile by ID.
     *
     * @param userId the user's ID
     * @return UserDTO with profile info
     */
    @Transactional(readOnly = true)
    public UserDTO getUserProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found."));

        return UserDTO.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .coins(user.getCoins())
                .gamesPlayed(user.getGamesPlayed())
                .gamesWon(user.getGamesWon())
                .isGuest(user.getIsGuest())
                .build();
    }

    /**
     * Update user profile settings (name and avatar).
     */
    @Transactional
    public UserDTO updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found."));
        
        if (request.getName() != null && !request.getName().trim().isEmpty()) {
            user.setName(request.getName().trim());
        }
        user.setAvatarUrl(request.getAvatarUrl());
        
        user = userRepository.save(user);
        return getUserProfile(user.getId());
    }
}
